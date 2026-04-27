# Week 1 Mission — Proxy Diagram + Self-Invocation Experiment

> 제출 전 체크: 다이어그램과 실험 결과 둘 다 있어야 한다.

---

## Part 1. Proxy 호출 흐름 다이어그램

`@Transactional` 메서드가 호출될 때 객체 흐름을 직접 그려라.

### 요구사항

- **JDK Dynamic Proxy** 버전과 **CGLIB** 버전을 **각각** 그려라
- 각 다이어그램에 반드시 포함해야 하는 요소:
  - `Caller` (외부 호출자)
  - `Proxy` 객체 (클래스명 포함: `$Proxy0` vs `$$EnhancerBySpringCGLIB$$`)
  - `InvocationHandler` / `MethodInterceptor`
  - `Target` (실제 구현체)
  - `Connection` (ThreadLocal 경유 표시)
  - `DB`
- 두 프록시의 **관계(형제 vs 자식)** 를 클래스 계층으로 표현하라
- `ConnectionHolder`(ThreadLocal)가 어느 시점에 개입하는지 표시하라

### 형식

텍스트 다이어그램(ASCII)이어도 되고, 손으로 그린 사진이어도 된다. 중요한 건 흐름이 정확한가다.

---

## Part 2. Self-Invocation 우회 실험 보고서

**Spring Boot 프로젝트**를 새로 만들어서 실험하라.

### 전제 시나리오

```
OrderService.placeOrder()  ← 트랜잭션 없음
    └─ this.validateAndSave()  ← @Transactional
```

외부에서 `placeOrder()`를 호출하면 `validateAndSave()`의 `@Transactional`이 동작하지 않는다.

이것을 3가지 방법으로 우회하고, 각각 **실제로 트랜잭션이 열렸는지** 확인하라.

### 3가지 우회 방법

| 방법 | 설명 |
|------|------|
| 방법 1 | Self-Injection (`@Autowired` 자기 자신) |
| 방법 2 | 별도 Bean으로 분리 |
| 방법 3 | AspectJ (compile-time weaving) |

### 각 방법마다 기록해야 하는 것

1. **트랜잭션이 실제로 열렸는가?**
   - `TransactionSynchronizationManager.isActualTransactionActive()` 로그로 확인
2. **코드 변경 범위** (얼마나 많은 클래스/설정이 바뀌었는가)
3. **트레이드오프** (장점 / 단점 각 1줄 이상)
4. **언제 이 방법을 써야 하는가** (실무 기준)

### 보고서 형식

각 방법을 아래 형식으로 작성:

```
### 방법 N: [이름]

**결과**: 트랜잭션 열림 / 안 열림

**코드**:
(핵심 변경 부분만 — 전체 코드 붙여넣기 X)

**트레이드오프**:
- 장점:
- 단점:

**언제 쓰는가**:
```

---

## 제출 기준

멘토가 아래 질문을 던진다. 답할 수 있으면 통과:

1. JDK Proxy와 CGLIB의 클래스 계층 관계를 설명하라. 왜 CGLIB는 `private` 메서드를 가로챌 수 없는가?
2. Self-Injection 방법의 단점이 "순환 참조처럼 보인다"는 것 외에 또 뭐가 있는가?
3. AspectJ를 선택하지 말아야 하는 상황은?
4. 별도 Bean 분리가 가장 권장되는 이유를 트랜잭션 메커니즘 관점에서 설명하라.

---

> 제출할 준비가 되면: "W1 미션 제출" 이라고 말해라.
