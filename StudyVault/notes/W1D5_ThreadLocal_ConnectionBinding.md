# W1D5 — PlatformTransactionManager & Connection Binding: ThreadLocal의 등장

> **학습 목표**: Spring이 트랜잭션 범위 내에서 여러 Repository에게 같은 커넥션을 보장하는 메커니즘을 이해하고, `ConnectionHolder`를 직접 구현해 증명한다.

---

## 1. MiniTxManager v1의 치명적 결함

D4에서 만든 `TransactionHandler`는 생성자로 `Connection`을 주입받았다.

```java
public class TransactionHandler implements InvocationHandler {
    private final Connection connection;  // ← 생성자 주입
```

실제 Spring 앱에서는 `OrderRepository`, `InventoryRepository` 등 여러 Repository가 체인으로 호출된다. 문제는 각 Repository가 `DataSource.getConnection()`을 직접 호출한다는 것 — **TransactionHandler가 열어둔 커넥션과 다른 커넥션을 쓸 수 있다.**

---

## 2. 핵심 질문

> `TransactionHandler`와 `OrderRepository`는 서로 모른다. 어떻게 같은 커넥션을 쓸 수 있는가?

두 객체가 **명시적으로 커넥션을 주고받지 않아도** 공유할 수 있는 공간이 필요하다.

**답: ThreadLocal** — 스레드 단위로 격리된 저장소.

---

## 3. 왜 static Map이 아닌가?

### ConcurrentHashMap으로 대체하면 안 되나?

```
ConcurrentHashMap<???, Connection>
```

키를 뭘로 써야 할까? → `Thread.currentThread()` 또는 `Thread.getId()`

그런데 그게 결국 **ThreadLocal이 내부적으로 하는 일**이다.

| | static Map | ThreadLocal |
|---|---|---|
| Thread-safe | ConcurrentHashMap으로 가능 | 기본 제공 |
| 스레드 격리 | 키 관리를 직접 해야 함 | 자동 격리 |
| 정리(cleanup) | 직접 remove() 해야 함 | remove() 제공, 구조적으로 명확 |
| 메모리 누수 위험 | 키 안 지우면 누수 | ThreadLocal도 remove() 안 하면 누수 |

**핵심**: ConcurrentHashMap은 동시 수정 안전이지, 스레드 간 격리가 아니다.

---

## 4. ConnectionHolder 구현

```java
public class ConnectionHolder {
    private static final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();

    public static void saveThreadConnection(Connection connection) {
        connectionHolder.set(connection);
    }

    public static Connection getThreadConnection() {
        return connectionHolder.get();
    }

    public static void deleteThreadConnection() {
        connectionHolder.remove();
    }
}
```

**이게 Spring `TransactionSynchronizationManager`의 핵심 구조다.**

Spring은 `ThreadLocal<Map<Object, Object>>`를 사용해서 키는 `DataSource`, 값은 `ConnectionHolder`로 저장한다. 여러 DataSource를 동시에 관리할 수 있게.

---

## 5. TransactionHandler v3 (ThreadLocal 기반)

```java
public class TransactionHandler implements InvocationHandler {
    private final Object target;
    private final DataSource dataSource;  // ← Connection 대신 DataSource

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Connection connection = dataSource.getConnection();
        ConnectionHolder.saveThreadConnection(connection);  // ← ThreadLocal에 저장
        connection.setAutoCommit(false);
        try {
            Object result = method.invoke(target, args);
            connection.commit();
            return result;
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            ConnectionHolder.deleteThreadConnection();  // ← 반드시 정리
        }
    }
}
```

**`finally` 블록이 중요한 이유**: 커밋이든 롤백이든 예외든 어떤 경로로 끝나도 ThreadLocal을 정리해야 한다. 안 하면 커넥션 풀 반납 후에도 ThreadLocal에 닫힌 커넥션이 남아 다음 요청에서 꺼내 쓸 수 있다.

---

## 6. 직접 증명 — identityHashCode

```java
// TransactionHandler.invoke()
System.out.println("HASHING = " + System.identityHashCode(connection));

// OrderServiceImpl.placeOrder()
System.out.println("HASHING = " + System.identityHashCode(ConnectionHolder.getThreadConnection()));
```

### 실행 결과
```
[BEGIN] ==================
HASHING = 255944888
HASHING = 255944888   ← 같은 객체!
[ROLLBACK] ==================
```

`TransactionHandler`에서 열고 `ConnectionHolder`에 저장한 커넥션과, `OrderServiceImpl`에서 `ConnectionHolder`로 꺼낸 커넥션이 **동일한 객체**임이 증명됐다.

> `hashCode()` 대신 `System.identityHashCode()`를 쓰는 이유: `hashCode()`는 오버라이드될 수 있다. `identityHashCode()`는 항상 JVM의 객체 메모리 주소 기반이라 신뢰할 수 있다.

---

## 7. Spring의 실제 흐름

```
@Transactional 메서드 호출
        ↓
TransactionInterceptor.invoke()
        ↓
PlatformTransactionManager.getTransaction()
        ↓
DataSourceUtils.getConnection(dataSource)
        ↓ (이미 ThreadLocal에 있으면 그것 반환, 없으면 새로 열기)
TransactionSynchronizationManager.bindResource(dataSource, connectionHolder)
        ↓ (ThreadLocal에 저장)
비즈니스 로직 실행
        ↓
Repository에서 DataSourceUtils.getConnection() 호출
        ↓ (ThreadLocal에서 꺼냄 → 같은 커넥션)
commit 또는 rollback
        ↓
TransactionSynchronizationManager.unbindResource()
```

---

## 8. @Async + @Transactional 함정 예고

```java
@Async  // 새 스레드에서 실행
@Transactional
public void asyncMethod() {
    ConnectionHolder.getThreadConnection();  // → null 또는 전혀 다른 커넥션
}
```

ThreadLocal은 **스레드 당 격리**다. `@Async`로 생성된 새 스레드는 부모 스레드의 ThreadLocal을 볼 수 없다. 즉, 부모의 트랜잭션이 자식 스레드로 **전파되지 않는다**.

→ W2D5에서 본격적으로 다룬다.

---

## 9. Week 1 전체 정리

| Day | 핵심 발견 |
|-----|---------|
| D1 | `autocommit=true`는 기본값이지만 위험하다. `setAutoCommit/commit/rollback`이 트랜잭션의 전부다 |
| D2 | `@Transactional`은 프록시가 메서드를 가로채는 것. JDK Proxy(형제) vs CGLIB(자식) |
| D3 | `this.method()`는 프록시를 우회한다. 외부 호출만 가로챌 수 있다 |
| D4 | `REQUIRES_NEW`는 커넥션 2개를 동시에 점유 → 풀 크기 잘못 설정하면 데드락 |
| D5 | 같은 스레드의 객체들이 커넥션을 공유하는 비결은 **ThreadLocal** |

---

## 10. 한 줄 요약

> **Spring이 "같은 커넥션"을 보장하는 비결은 ThreadLocal이다. 트랜잭션 시작 시 커넥션을 ThreadLocal에 바인딩하고, Repository는 DataSource 대신 ThreadLocal에서 꺼내 쓴다. 이것이 `TransactionSynchronizationManager`의 정체다.**
