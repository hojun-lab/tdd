# W1D3 — Self-invocation 함정: @Transactional이 조용히 무시되는 순간

> **학습 목표**: `this.method()`로 호출한 `@Transactional` 메서드가 왜 동작하지 않는지 직접 재현하고, 우회 방법 3가지를 이해한다.

---

## 1. 실무에서 가장 흔한 함정

```java
@Service
public class OrderService {

    public void placeOrder() {
        this.validateAndSave();  // ← 같은 클래스 안에서 호출
    }

    @Transactional
    public void validateAndSave() {
        orderRepo.save(order);
    }
}
```

외부에서 `orderService.placeOrder()`를 호출하면 —
**`validateAndSave()`의 `@Transactional`은 동작하지 않는다.**

에러도 없고, 로그도 없고, 경고도 없다. **그냥 조용히 무시된다.**

---

## 2. 왜? — 프록시 구조를 다시 보자

어제(D2) 만든 흐름:

```
외부 호출: proxy.placeOrder()
           ↓
$Proxy0.placeOrder()      ← 런타임 생성된 프록시
           ↓
handler.invoke(...)       ← 여기서 AOP 로직 (BEFORE/AFTER/트랜잭션)
           ↓
OrderServiceImpl.placeOrder()   ← 진짜 구현체
```

그런데 `OrderServiceImpl.placeOrder()` 안에서 `this.validateAndSave()`를 호출하면?

**`this`는 `OrderServiceImpl` 본인**이지, 프록시가 아니다.

```
OrderServiceImpl.placeOrder()
           ↓ this.validateAndSave()
OrderServiceImpl.validateAndSave()   ← 프록시 우회!
           ↓
❌ handler.invoke() 호출 안 됨
❌ @Transactional 로직 실행 안 됨
```

---

## 3. 직접 재현해보기

### 실험 설계

어제 만든 `LoggingHandler` 그대로 사용.

```java
// OrderService 인터페이스
public interface OrderService {
    void placeOrder(long userId, long amount);
    void validateAndSave(long userId, long amount);  // ← 추가
}

// OrderServiceImpl
public class OrderServiceImpl implements OrderService {
    public void placeOrder(long userId, long amount) {
        System.out.println("주문 처리: userId=" + userId);
        this.validateAndSave(userId, amount);  // ← self-invocation
    }

    public void validateAndSave(long userId, long amount) {
        System.out.println("validate & save: done");
    }
}
```

### 실행 결과

```
[BEFORE] placeOrder      ← 외부 호출, 프록시 가로챔
주문 처리: userId=1
validate & save: done    ← 실행은 됨
                         ← [BEFORE] validateAndSave 없음!
                         ← [AFTER] validateAndSave 없음!
[AFTER] placeOrder
```

**`validateAndSave()`는 실행됐지만, 프록시를 거치지 않았다.**

만약 `validateAndSave()`에 `@Transactional`이 걸려있었다면, **트랜잭션이 열리지 않는다.**

---

## 4. 프로덕션에서 이게 왜 무서운가

### 증상
- 컴파일 통과 ✓
- 런타임 예외 없음 ✓
- 로그 깨끗함 ✓
- `@Transactional` 어노테이션은 코드에 분명히 있음 ✓

### 문제
- 트랜잭션이 **열리지 않음**
- 예외 발생 시 rollback **안 됨**
- 데이터 정합성 **깨질 수 있음**

### 디버깅의 지옥
"어노테이션이 있는데 왜 트랜잭션이 안 걸리지?" 
→ 코드에 버그 없음 
→ 설정도 정상 
→ 원인을 찾는 데 며칠 걸리는 경우 많음

---

## 5. 우회 방법 3가지

### 방법 1: Self-Injection

자기 자신을 주입받아서 `this` 대신 쓴다. 주입된 것은 **프록시**이므로 AOP가 동작한다.

```java
@Service
public class OrderService {
    @Autowired
    private OrderService self;  // ← 자기 자신 주입 (실제로는 프록시)

    public void placeOrder() {
        self.validateAndSave();  // ← this가 아니라 self
    }

    @Transactional
    public void validateAndSave() { ... }
}
```

**트레이드오프**: 
- ✅ 구조 변경 최소화
- ❌ 순환 참조처럼 보여 가독성 저하
- ❌ 코드 리뷰어가 "이게 왜 필요하지?" 라고 물음

### 방법 2: 다른 Bean으로 분리 (가장 권장)

`@Transactional` 메서드를 별도 클래스로 분리. 다른 Bean을 호출하면 자연스럽게 프록시를 거친다.

```java
@Service
public class OrderService {
    private final OrderValidator validator;

    public void placeOrder() {
        validator.validateAndSave();  // ← 다른 Bean → 프록시 경유
    }
}

@Service
public class OrderValidator {
    @Transactional
    public void validateAndSave() { ... }
}
```

**트레이드오프**:
- ✅ 책임 분리가 명확해짐
- ✅ 테스트하기 쉬움 (mock으로 분리 용이)
- ❌ 클래스 수 증가
- ✅ **실무에서 가장 많이 쓰는 방법**

### 방법 3: AspectJ (컴파일 타임 위빙)

Spring AOP는 **런타임 프록시** 방식이지만, AspectJ는 **컴파일 타임에 바이트코드에 AOP 로직을 직접 심는다**.

```java
// 컴파일 전
@Transactional
public void validateAndSave() {
    orderRepo.save(order);
}

// 컴파일 후 (개념적 표현)
public void validateAndSave() {
    setAutoCommit(false);
    try {
        orderRepo.save(order);
        commit();
    } catch (Exception e) {
        rollback();
        throw e;
    }
}
```

프록시가 **없다**. AOP 로직이 메서드 안에 박혀있어서 `this.method()`로 불러도 동작한다.

**트레이드오프**:
- ✅ self-invocation 문제 없음
- ✅ 성능 약간 우수 (프록시 호출 오버헤드 없음)
- ❌ 설정 복잡 (AspectJ Weaver 필요)
- ❌ 빌드 시간 증가
- ❌ 디버깅 어려움 (바이트코드가 변경됨)
- → 대규모 시스템에서만 고려

---

## 6. 핵심 개념 정리

| | JVM 레벨 직접 호출 (`this`) | 프록시 경유 호출 |
|---|---|---|
| 방식 | `this.method()` | `otherBean.method()` 또는 `self.method()` |
| AOP 동작 | ❌ 무시됨 | ✅ 동작함 |
| @Transactional | ❌ 트랜잭션 안 열림 | ✅ 정상 |

**규칙**: 프록시는 **외부에서 들어오는 호출만** 가로챌 수 있다.

---

## 7. 다음 단계로 가져갈 질문

- 만약 `@Transactional(propagation = REQUIRES_NEW)`를 self-invocation으로 호출하면 어떻게 될까?
- 여러 `@Transactional` 메서드가 체인으로 호출될 때, **같은 커넥션**을 쓸까, 아니면 각각 다른 커넥션을 쓸까?

---

## 8. 한 줄 요약

> **`this.method()`로 호출하는 순간 프록시는 투명해진다. `@Transactional`은 외부로부터의 호출에만 유효하다.**
