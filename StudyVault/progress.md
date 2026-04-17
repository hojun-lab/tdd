# @Transactional Deep-Dive Progress

> Learner: anonymous
> Started: 2026-04-13
> Current: Week 1, Day 2

## Week 1: Foundations (Build: NaiveTransactionManager)

| Day | Topic | Theory | Build | Date | Notes |
|-----|-------|--------|-------|------|-------|
| D1 | ACID & raw JDBC | DEEP | COMPLETE | 2026-04-13 | autocommit 동작, rollback 명시 필요성, 커넥션 풀 반납 시 dirty connection 위험 이해 |
| D2 | Spring AOP — CGLIB vs JDK Proxy | - | - | - | - |
| D3 | Self-invocation 함정 | - | - | - | - |
| D4 | Propagation 개요 | - | - | - | - |
| D5 | PlatformTransactionManager & Connection Binding | - | - | - | - |
| Mission | Proxy diagram + Self-invocation experiment | - | - | - | - |

## Week 2: Internals (Build: ProxyInterceptor)

| Day | Topic | Theory | Build | Date | Notes |
|-----|-------|--------|-------|------|-------|
| D1 | TransactionSynchronizationManager (ThreadLocal) | - | - | - | - |
| D2 | readOnly=true 실제 동작 | - | - | - | - |
| D3 | Rollback rules | - | - | - | - |
| D4 | Propagation 심화 (NESTED, MANDATORY 등) | - | - | - | - |
| D5 | @Async + @Transactional 함정 | - | - | - | - |
| Mission | Propagation defense + Internals report | - | - | - | - |

## Week 3: Production Mastery (Build: BreakIt)

| Day | Topic | Theory | Build | Date | Notes |
|-----|-------|--------|-------|------|-------|
| D1 | LazyInitializationException & OSIV | - | - | - | - |
| D2 | N+1 & dirty checking | - | - | - | - |
| D3 | private/protected 메서드 함정 | - | - | - | - |
| D4 | 테스트에서의 @Transactional 함정 | - | - | - | - |
| D5 | 분산 트랜잭션 — XA, Saga, Outbox | - | - | - | - |
| Mission | Incident diagnosis + MiniTxManager retrospective | - | - | - | - |

## Build Versions

| Version | Description | Status | Key Learning |
|---------|-------------|--------|--------------|
| v0 | Raw JDBC (baseline) | COMPLETE | autocommit/commit/rollback 직접 실험으로 트랜잭션 경계 체득 |
| v1 | Manual begin/commit/rollback | - | - |
| v2 | JDK Dynamic Proxy interceptor | - | - |
| v3 | + ThreadLocal ConnectionHolder | - | - |
| v4 | + propagation (REQUIRED/REQUIRES_NEW) | - | - |
| v5 | + rollback rules + readOnly | - | - |
| Final | vs Spring TransactionInterceptor | - | - |

## Concept Mastery

| Concept | Status | First Seen | Last Reviewed |
|---------|--------|------------|---------------|
| ACID | - | - | - |

> Theory Status: DEEP / SURFACE / NEEDS-REVIEW / NOT-STARTED
> Build Status: COMPLETE / IN-PROGRESS / NOT-STARTED
