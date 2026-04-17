# W1D1 — ACID와 Raw JDBC: @Transactional이 필요한 이유

> **학습 목표**: `@Transactional` 없이 순수 JDBC만으로 트랜잭션을 다뤄보며, Spring이 왜 존재하는지 몸으로 느낀다.

---

## 1. 시작하기 전에 — ACID가 뭐였더라?

| 속성 | 의미 |
|---|---|
| **Atomicity** (원자성) | 여러 작업이 "전부 성공"하거나 "전부 실패" |
| **Consistency** (일관성) | 트랜잭션 전후에 DB 제약 조건 유지 |
| **Isolation** (격리성) | 동시 실행되는 트랜잭션끼리 간섭 안 함 |
| **Durability** (지속성) | commit된 결과는 영구 저장 |

오늘은 **Atomicity**에 집중. 두 개의 INSERT가 원자적으로 묶이지 않으면 어떤 일이 벌어지는가?

---

## 2. 핵심 질문

> JDBC로 INSERT 두 개를 순서대로 실행한다. 첫 번째 직후 예외가 터지면 첫 번째 데이터는 DB에 어떻게 되어 있는가?

직관적 예상: "사라져야 한다. 한 묶음이니까."
현실: **autocommit이 기본값이라 즉시 커밋된다. 예외가 터져도 이미 늦다.**

---

## 3. 실험 설계

3가지 케이스를 JDBC로 직접 재현:

| 케이스 | 설정 | catch 시점 | 최종 DB |
|---|---|---|---|
| **Case 1** | `autocommit=true` (기본) | 데이터 보임 | **남음** |
| **Case 2** | `autocommit=false`, rollback 호출 안 함 | 데이터 보임 | **사라짐** (커넥션 닫힐 때 자동 rollback) |
| **Case 3** | `autocommit=false` + 명시적 `rollback()` | 데이터 안 보임 | **사라짐** (즉시) |

### 핵심 관찰 포인트

- **Case 1의 공포**: 주문은 저장됐는데 포인트 차감 INSERT는 실행조차 안 됨 → 데이터 불일치
- **Case 2의 함정**: "커넥션 닫히면 자동 rollback되네?" 라고 방심하면 안 됨
- **Case 3가 정답**: 명시적 rollback이 유일한 신뢰할 수 있는 방법

---

## 4. JDBC API — 트랜잭션 제어 메서드 3총사

```java
// START TRANSACTION (DB 관점)
conn.setAutoCommit(false);

// 성공 시
conn.commit();

// 실패 시
conn.rollback();
```

`@Transactional`이 결국 **이 세 메서드를 대신 호출해주는 장치**라는 걸 기억하자.

---

## 5. 실험 코드 구조

```
main()
├── DriverManager.getConnection()   ← try-with-resources로 커넥션 획득
├── setupTables(conn)               ← 테이블 DDL
├── Case 1 실험 (autocommit=true)
├── Case 2 실험 (autocommit=false, no rollback)
└── Case 3 실험 (autocommit=false + rollback)
```

### 실험에서 배운 것

1. **Statement 1개로 여러 SELECT를 돌리면 이전 ResultSet이 닫힌다**
   - 같은 테이블이라도 두 ResultSet이 필요하면 Statement 두 개 써야 함

2. **같은 Connection 안에서는 미커밋 데이터도 보인다**
   - Case 2에서 rollback 호출 안 했어도 `printTableState()`는 데이터를 보여줌
   - 하지만 커넥션 닫히면 사라짐 → "보인다 ≠ 커밋됐다"

3. **try-with-resources의 블록이 끝나면 Connection이 닫힌다**
   - 닫힐 때 미커밋 트랜잭션은 DB가 자동으로 rollback 처리

---

## 6. 프로덕션 트랩 — Connection Pool 환경

HikariCP 같은 **커넥션 풀** 환경에서는 `conn.close()`가 실제로는 커넥션을 **풀에 반납**한다. 물리적으로 닫히지 않음.

### Case 2 방식의 위험 (rollback 없이 반납)

```
스레드 A
  INSERT (미커밋)
  예외 발생
  rollback() 호출 안 함
  conn.close() → 풀에 반납 (미커밋 트랜잭션 열린 채로!)

스레드 B
  같은 커넥션 획득
  자기 INSERT + commit()
  ⚠️ 스레드 A의 미커밋 데이터까지 같이 커밋될 수 있음
```

**결론: rollback은 catch 블록에서 명시적으로 호출해야 한다.**

---

## 7. 이게 `@Transactional`이 존재하는 이유

매 메서드마다 이 패턴을 직접 짜야 한다면?

```java
Connection conn = dataSource.getConnection();
conn.setAutoCommit(false);
try {
    // 비즈니스 로직 5줄
    conn.commit();
} catch (Exception e) {
    conn.rollback();
    throw e;
} finally {
    conn.close();
}
```

비즈니스 로직 5줄에 보일러플레이트 10줄. 100개 메서드면? **실수 안 할 수 있을까?**

Spring은 이걸 AOP로 **자동화**한다. 어노테이션 하나로 끝낸다. 내일(D2)은 이 자동화의 실체를 파본다.

---

## 8. 다음 단계로 가져갈 질문

- Spring은 어떻게 **어노테이션 하나만으로** 저 패턴을 주입할까?
- 프록시는 어떻게 **원본 메서드를 가로챌까**?
- 프록시는 `$$EnhancerBySpringCGLIB$$` 같은 이상한 이름을 갖는데, 이건 누가 언제 만든 클래스인가?

---

## 9. 한 줄 요약

> **autocommit=true는 편리해 보이지만, 트랜잭션 경계가 없는 위험한 기본값이다. `@Transactional`은 이 기본값과 싸우기 위해 존재한다.**
