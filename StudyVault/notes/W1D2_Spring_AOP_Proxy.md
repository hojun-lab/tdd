# W1D2 — Spring AOP: CGLIB vs JDK Dynamic Proxy

> **학습 목표**: `@Transactional`이 붙은 메서드 호출이 "어떻게 가로채지는가"의 정체를 직접 만들어본다.

---

## 1. 핵심 질문

> `@Transactional`이 붙은 클래스를 Spring 컨테이너에서 꺼내서 `getClass().getName()`을 찍으면 뭐가 나올까?

답: **네가 만든 그 클래스가 아니다.**

```
네가 만든 클래스:  com.example.OrderService
실제로 반환되는 것:  com.example.OrderService$$EnhancerBySpringCGLIB$$1a2b3c4d
```

이 이상한 이름의 클래스는 누가 언제 만든 건가?

---

## 2. 두 가지 프록시 전략

Spring은 상황에 따라 두 가지 프록시 전략을 쓴다.

### JDK Dynamic Proxy
- **전략**: 인터페이스를 `implements`
- **조건**: 인터페이스 필수
- **클래스명**: `jdk.proxy1.$Proxy0`
- **관계**: 원본 구현체와 **형제** (같은 인터페이스 구현)

```
        OrderService (interface)
        /           \
OrderServiceImpl   $Proxy0
   (네가 작성)     (런타임 생성)
```

### CGLIB
- **전략**: 원본 클래스를 `extends`
- **조건**: 인터페이스 없어도 됨
- **클래스명**: `$$EnhancerBySpringCGLIB$$...`
- **관계**: 원본 클래스의 **자식** (상속)

```
OrderService
    ↑
OrderService$$EnhancerBySpringCGLIB
```

### Spring Boot 2.x+ 기본값

**CGLIB** (인터페이스가 있어도). `proxy-target-class=true`가 기본값.

---

## 3. JDK Dynamic Proxy 직접 만들기

필요한 요소 3가지:

```java
// 1. 인터페이스
public interface OrderService {
    void placeOrder(long userId, long amount);
}

// 2. 원본 구현체
public class OrderServiceImpl implements OrderService {
    public void placeOrder(long userId, long amount) {
        System.out.println("주문 처리: userId=" + userId);
    }
}

// 3. InvocationHandler (가로채기 로직)
public class LoggingHandler implements InvocationHandler {
    private final Object target;

    public LoggingHandler(Object target) { this.target = target; }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("[BEFORE] " + method.getName());
        Object result = method.invoke(target, args);  // ← 실제 메서드 호출
        System.out.println("[AFTER] " + method.getName());
        return result;
    }
}
```

### 프록시 생성

```java
OrderService target = new OrderServiceImpl();
OrderService proxy = (OrderService) Proxy.newProxyInstance(
    target.getClass().getClassLoader(),
    new Class[]{ OrderService.class },
    new LoggingHandler(target)
);

System.out.println(proxy.getClass().getName());
// 출력: jdk.proxy1.$Proxy0

proxy.placeOrder(1L, 1000L);
// [BEFORE] placeOrder
// 주문 처리: userId=1
// [AFTER] placeOrder
```

---

## 4. 호출 흐름 정리

```
네 코드:         proxy.placeOrder(1, 1000)
                      ↓
런타임 생성 클래스:   $Proxy0.placeOrder()
                      ↓ (내부에서 InvocationHandler 호출)
네 코드:         LoggingHandler.invoke(proxy, method, args)
                      ↓ (method.invoke로 실제 호출)
네 코드:         OrderServiceImpl.placeOrder(1, 1000)
```

**`LoggingHandler.invoke()`의 `[BEFORE]`과 `[AFTER]` 사이**에 `setAutoCommit(false)`와 `commit()/rollback()`을 넣으면? 

→ 그게 바로 `@Transactional`의 정체다.

---

## 5. CGLIB는 왜 필요한가?

JDK Dynamic Proxy는 **인터페이스가 없으면 프록시를 만들 수 없다**.

```java
public class UserService {  // implements 없음
    @Transactional
    public void createUser() { ... }
}
```

이 클래스에 `@Transactional`을 걸려면? **CGLIB이 필요하다.**

CGLIB는 **상속**으로 프록시를 만들기 때문에 인터페이스가 필요 없다.

### CGLIB의 제약

상속으로 만들기 때문에 **오버라이드할 수 없는 메서드는 프록시가 가로챌 수 없다**:

| 제약 | 이유 |
|---|---|
| `private` 메서드 | 서브클래스에서 접근 불가 |
| `static` 메서드 | 인스턴스 메서드가 아니라 오버라이드 개념 없음 |
| `final` 메서드 | 문법적으로 오버라이드 불가 |
| `final` 클래스 | 상속 자체가 불가 |

### 프로덕션 트랩

```java
@Service
public class OrderService {
    @Transactional
    private void processOrder() { ... }  // ⚠️ private!
}
```

- 컴파일 에러 없음
- 런타임 에러 없음
- **그런데 `@Transactional`이 조용히 무시됨**
- CGLIB 프록시가 `private` 메서드를 가로챌 수 없기 때문

---

## 6. 배운 것 정리

| | JDK Dynamic Proxy | CGLIB |
|---|---|---|
| 전략 | `implements` interface | `extends` class |
| 인터페이스 필요 | 예 | 아니오 |
| 제약 | 인터페이스에 있는 메서드만 | `private`/`static`/`final`/`final class` 불가 |
| 클래스명 | `$Proxy0` | `$$EnhancerBySpringCGLIB$$` |
| Spring Boot 기본 | (과거) | **현재 기본값** |

---

## 7. 다음 단계로 가져갈 질문

- `this.method()` 라고 같은 클래스 안에서 호출하면 프록시가 동작할까?
- 만약 동작 안 한다면, 왜?
- 이걸 어떻게 우회할 수 있을까?

---

## 8. 한 줄 요약

> **`@Transactional`은 마법이 아니다. `Proxy.newProxyInstance()`가 런타임에 생성한 서브 클래스가 `invoke()` 메서드에서 `setAutoCommit/commit/rollback`을 부르는 것 — 이게 전부다.**
