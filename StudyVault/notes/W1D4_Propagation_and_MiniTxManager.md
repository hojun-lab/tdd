# W1D4 — Propagation과 MiniTxManager v1: @Transactional 직접 만들어보기

> **학습 목표**: `REQUIRED`와 `REQUIRES_NEW`의 실제 동작을 이해하고, 프록시에 트랜잭션 로직을 박아 `@Transactional`의 핵심을 직접 구현한다.

---

## 1. Transaction Propagation — 트랜잭션의 "경계"를 정하는 옵션

`@Transactional(propagation = ...)` 옵션이 왜 존재하는가?

### 시나리오

```java
@Service
class OrderService {
    @Transactional  // 기본값: REQUIRED
    public void placeOrder() {
        orderRepo.save(order);
        auditService.log();  // ← 이 안에 또 @Transactional이 있음
    }
}

@Service
class AuditService {
    @Transactional(propagation = REQUIRES_NEW)
    public void log() {
        auditRepo.save(entry);
    }
}
```

**질문**: 트랜잭션이 몇 개 열릴까? 각각 어떤 커넥션을 쓸까?

---

## 2. REQUIRED — 기본값

이름 해석: "트랜잭션이 **필요하다**"

| 상황 | 동작 |
|---|---|
| 기존 트랜잭션 있음 | 그 트랜잭션에 **참여(join)** |
| 기존 트랜잭션 없음 | **새로 생성** |

비유: 회의실에 이미 누가 회의 중이면 그 회의에 들어가고, 없으면 내가 회의를 연다.

### 특징
- **하나의 커넥션**만 사용
- 어느 한 메서드가 예외를 던지면 **전체 트랜잭션이 롤백**
- 실무에서 99% 이 옵션을 사용

---

## 3. REQUIRES_NEW — 무조건 새로 만든다

| 상황 | 동작 |
|---|---|
| 기존 트랜잭션 있음 | 기존 트랜잭션 **일시정지** + **새 트랜잭션 생성** |
| 기존 트랜잭션 없음 | 새로 생성 |

### 핵심 메커니즘

1. 외부 트랜잭션(커넥션 A)이 열려있는 상태에서 REQUIRES_NEW 호출
2. **커넥션 A를 그대로 쥐고** (반납 X, 일시정지만)
3. 새 커넥션 B 획득 → 새 트랜잭션 시작
4. 내부 메서드 실행 → B에서 commit/rollback
5. 커넥션 A의 트랜잭션 **재개**

### 결과: 한 스레드가 동시에 **커넥션 2개**를 점유한다

---

## 4. 프로덕션 재앙 — REQUIRES_NEW 데드락

HikariCP `maxPoolSize = 5` 환경에서 동시 5개 요청이 REQUIRES_NEW 패턴을 실행한다면?

```
스레드 1: 커넥션 A 점유 → 커넥션 B 요청 (대기)
스레드 2: 커넥션 C 점유 → 커넥션 D 요청 (대기)
스레드 3: 커넥션 E 점유 → 커넥션 ? 요청 (풀 비어있음)
스레드 4: 커넥션 획득 실패, 대기
스레드 5: 커넥션 획득 실패, 대기
```

**결과**: 모두 커넥션 1개씩 쥐고 나머지를 기다린다. 아무도 양보하지 않는다. `connectionTimeout` (기본 30초) 후 전부 실패.

### 이건 단순 고갈이 아니라 **데드락**이다
- 일반 고갈: 누가 곧 반납할 거라 대기만 하면 해결됨
- REQUIRES_NEW 데드락: 쥐고 있는 걸 놓으려면 추가 커넥션이 필요 → 추가 커넥션이 없음 → 영원히 못 끝남

### 공식
```
poolSize ≥ maxConcurrent × connectionsPerRequest
```

REQUIRES_NEW 쓰면 `connectionsPerRequest = 2`. 중첩되면 3, 4, ...

---

## 5. 해결책 3가지

### 1. 풀 크기 조정
- ✅ 간단
- ❌ DB `max_connections` 한계 있음
- ❌ 컨텍스트 스위칭 비용 증가 (HikariCP 문서 참고)
- 근본 해결 아님

### 2. REQUIRES_NEW 쓰지 않기
- 대부분의 경우 REQUIRED로 해결됨
- 꼭 "독립 트랜잭션"이 필요한 경우만 REQUIRES_NEW 쓴다
  - 예: 감사 로그 (메인 트랜잭션이 롤백되어도 로그는 남아야 함)

### 3. 별도 DataSource (격리된 풀)
- **"더 많은 커넥션"이 아니라 "격리된 풀"**
- 주요 비즈니스용 풀과 감사 로그용 풀을 분리
- 두 풀이 서로 **경쟁 자체를 하지 않음**

```java
@Bean("mainDataSource")
public DataSource mainDs() { /* maxPoolSize=10 */ }

@Bean("auditDataSource")
public DataSource auditDs() { /* maxPoolSize=5 */ }
```

**트레이드오프**:
- ✅ 구조적 격리 (데드락 불가)
- ❌ DB 총 커넥션 수 증가
- ❌ 서로 다른 트랜잭션 → 원자성 없음 (감사 로그는 오히려 이게 올바를 수 있음)

---

## 6. Build Track — MiniTxManager v1

오늘 드디어 **진짜 `@Transactional`을 직접 만든다.**

D2에서 만든 `LoggingHandler`의 `[BEFORE]/[AFTER]` 자리에 실제 트랜잭션 로직을 박는다.

### TransactionHandler.java

```java
public class TransactionHandler implements InvocationHandler {
    private final Object target;
    private final Connection connection;

    public TransactionHandler(Object target, Connection connection) {
        this.target = target;
        this.connection = connection;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("[BEGIN]");
        connection.setAutoCommit(false);
        try {
            Object result = method.invoke(target, args);
            System.out.println("[COMMIT]");
            connection.commit();
            return result;
        } catch (Exception e) {
            System.out.println("[ROLLBACK]");
            connection.rollback();
            throw e;
        }
    }
}
```

---

## 7. 실행 결과 — 네가 방금 만든 것

### 정상 케이스
```
[BEGIN]        ← setAutoCommit(false)
주문 처리: userId=1
[COMMIT]       ← commit()
id=1           ← DB에 진짜 저장됨 ✓
```

### 실패 케이스 (INSERT 후 RuntimeException)
```
[BEGIN]
[ROLLBACK]     ← rollback() 정상 호출
(예외 전파)
```

**네가 방금 증명한 것**:

> `@Transactional` = 프록시 + `(setAutoCommit/commit/rollback)`

마법이 없다. 이게 전부다.

---

## 8. 예외 래핑 이해하기

실패 케이스에서 이런 스택트레이스가 나왔다:

```
UndeclaredThrowableException (JDK Proxy가 감쌈)
  ↓
InvocationTargetException (method.invoke()가 감쌈)
  ↓
RuntimeException (원본)
```

`method.invoke()`는 타겟 메서드의 예외를 `InvocationTargetException`으로 감싼다. 그래서 원본 예외를 보려면 `e.getTargetException()` 또는 `e.getCause()`로 꺼내야 한다.

Spring의 `TransactionInterceptor`는 이걸 **자동으로 풀어서** 원본 예외를 던진다. 그래서 Spring 환경의 스택트레이스는 깔끔하다.

---

## 9. Spring이 내 `TransactionHandler`와 다른 점

네가 만든 건 핵심 메커니즘이고, Spring은 여기에 추가 레이어를 얹는다:

| | 내 MiniTxManager v1 | Spring |
|---|---|---|
| 프록시 | JDK Proxy 수동 생성 | 어노테이션 기반 자동 |
| 트랜잭션 관리 | `Connection` 직접 주입 | `PlatformTransactionManager` 추상화 (JDBC/JPA/JTA 공통) |
| Propagation | 지원 안 함 | `REQUIRED`, `REQUIRES_NEW`, `NESTED` 등 |
| Isolation | 지원 안 함 | `READ_COMMITTED`, `REPEATABLE_READ` 등 |
| Rollback 규칙 | 모든 Exception | 기본 `RuntimeException`만, `rollbackFor` 옵션 |
| 커넥션 공유 | 생성자 주입 | `ThreadLocal`로 자동 바인딩 ← **내일(D5) 주제** |

---

## 10. 다음 단계로 가져갈 질문

- Spring은 어떻게 Repository가 여러 개 체인으로 호출되어도 **같은 커넥션**을 쓰게 할까?
- `ThreadLocal`이 여기서 어떤 역할을 하는가?
- 만약 멀티 스레드에서 같은 데이터소스를 쓴다면 어떻게 격리되는가?

---

## 11. 한 줄 요약

> **`@Transactional`의 정체는 "프록시가 setAutoCommit, commit, rollback을 대신 호출해주는 것" 이다. 오늘 직접 만들어보면서 확인했다.**
