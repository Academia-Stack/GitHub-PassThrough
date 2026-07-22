# Aspect-Oriented Programming (AOP)

---

## What is AOP?

AOP is a programming paradigm that separates **cross-cutting concerns** from business logic.

A **cross-cutting concern** is behaviour that cuts across multiple layers/classes of an application — things that don't belong to any single class but are needed everywhere:

| Cross-cutting concern | Example |
|---|---|
| Security / authorization | Check role before every controller method |
| Logging | Log entry/exit of every service method |
| Transaction management | Open/commit/rollback DB transaction |
| **Rate limiting** | Acquire a token before every API call |
| Caching | Return cached result before hitting the DB |

Without AOP, you'd repeat this logic in every method. With AOP, you write it **once** and declare *where* it applies.

---

## Core Terminology

| Term | Plain English | This Project                                                                                                   |
|---|---|----------------------------------------------------------------------------------------------------------------|
| **Aspect** | The class containing the cross-cutting logic | `RateLimiterAspect` (for `Reselience4j`) and `CustomRateLimiterAspect` for custom rate-limiting implementation |
| **Advice** | *What* to do and *when* (Before / After / Around) | `@Before` — consume a token                                                                                    |
| **Pointcut** | An expression that selects *which* methods to intercept | `@annotation(RateLimited)`                                                                                     |
| **Join Point** | A specific moment where advice *can* be applied (always a method call in Spring AOP) | Call to `getAllUsers()`                                                                                        |
| **Weaving** | The process of linking aspects to target objects | Done at runtime by Spring (proxy-based)                                                                        |
| **Target Object** | The original bean being proxied | `GitHubRestClientService`                                                                                      |

> **Memory hook:** Aspect = *What*. Pointcut = *Where*. Advice = *When + Action*.

---

## How Spring AOP Works Internally

Spring AOP uses **JDK dynamic proxies** (for interface-backed beans) or **CGLIB proxies** (for class-backed beans) — no bytecode modification at compile time.

```
Caller
  │
  ▼
Spring Proxy (wraps the bean)
  │
  ├─ @Before advice runs  ← RateLimiterAspect.applyRateLimit()
  │
  ▼
Target Bean (actual method)
  │
  ▼
Return value
```

Because it's proxy-based, AOP **only works on Spring-managed beans** called from *outside* the bean. A method calling another method on `this` will bypass the proxy (self-invocation limitation).

---

## Types of Advice

| Annotation | When it runs | Typical use |
|---|---|---|
| `@Before` | Before the method | Auth check, rate limiting |
| `@After` | After the method (always) | Cleanup, audit logging |
| `@AfterReturning` | After a successful return | Log result |
| `@AfterThrowing` | After an exception | Error reporting |
| `@Around` | Wraps the method entirely | Timing, caching, transactions |

`@Around` is the most powerful — you control whether the target method even runs.

---

## Implementation in This Project

### Problem it solves

Before AOP, every service method had this boilerplate:

```java
public List<GitHubUser> getAllUsers() {
    rateLimiter.acquire();   // ← cross-cutting, not business logic
    return githubRestClient.get()...
}
```

This meant:
- `IGitHubRateLimiter` was injected into every service class
- If you forgot `acquire()` on a new method, rate limiting silently broke
- Unit tests for the service had to mock the rate limiter

### Solution: Custom annotation + Aspect

**Step 1 — Marker annotation** (`request/aop/annotation/RateLimited.java`)

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {}
```

`@Retention(RUNTIME)` is required — Spring AOP reads annotations at runtime via reflection.

**Step 2 — Aspect** (`request/aop/RateLimiterAspect.java`)

```java
@Aspect
@Component
public class RateLimiterAspect {

    private final IGitHubRateLimiter rateLimiter;

    public RateLimiterAspect(IGitHubRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Before("@annotation(request.aop.annotation.RateLimited)")
    public void applyRateLimit() {
        rateLimiter.acquire();
    }
}
```

**Step 3 — Usage** (service methods)

```java
@RateLimited
public List<GitHubUser> getAllUsers() {
    return githubRestClient.get().uri("/users")...
}
```

### Required dependency (`pom.xml`)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

This brings in `spring-aop` + `aspectjweaver`. Without `aspectjweaver`, the `@Aspect` annotation is not processed.

### Profile-aware rate limiter

The aspect injects `IGitHubRateLimiter`, which has two implementations selected by Spring profile:

| Profile | Bean | Strategy |
|---|---|---|
| `dev` | `GitHubRateLimiter` | Custom token-bucket (in-memory) |
| `prod` | `Resilience4jRateLimiterAdapter` | Resilience4j (configurable via YAML) |

The aspect has zero knowledge of which implementation is active — that's the point.

---

## Before vs After This Refactor

| | Before | After |
|---|---|---|
| Rate limiter injected into | Every service class | Only `RateLimiterAspect` |
| Adding rate limiting to new method | Remember to call `acquire()` | Add `@RateLimited` |
| Service unit tests | Must mock `IGitHubRateLimiter` | No rate limiter awareness needed |
| Forgot to add rate limiting | Silent bug | Impossible — just missing annotation |

---

## Best Practices

1. **Use `@Around` for anything that can short-circuit** (caching, auth) — `@Before` is for side effects only.
2. **Use fully-qualified annotation names in pointcut expressions** to avoid ambiguity across packages.
3. **Never use AOP inside the same bean** (self-invocation bypasses the proxy). Extract to a separate bean if needed.
4. **Keep advice methods small** — they are infrastructure, not business logic.
5. **Don't overuse AOP** — if logic is specific to one class, put it in that class. AOP is for behaviour that genuinely spans many classes.
6. **Test aspects independently** — test `RateLimiterAspect` directly by calling it with a mock `JoinPoint`, and test services without the aspect active.
7. **Prefer `@RetentionPolicy.RUNTIME`** on custom annotations — `CLASS` retention makes annotations invisible to Spring's runtime proxy weaving.

---

## Common Interview Questions

**Q: What is the difference between AOP and OOP?**
> OOP models *what things are* (nouns/classes). AOP models *what happens across things* (verbs/concerns). They complement each other — OOP structures the app, AOP handles behaviours that cross those structures.

**Q: What are the limitations of Spring AOP?**
> It is proxy-based, so: (1) self-invocation doesn't trigger advice, (2) it only works on Spring beans, (3) `private` methods cannot be intercepted. For full bytecode weaving, AspectJ (compile-time or load-time) is needed.

**Q: What is the difference between `@Before` and `@Around`?**
> `@Before` runs before the method but cannot prevent it from executing or change its return value. `@Around` wraps the method entirely — you call `joinPoint.proceed()` to invoke the target, meaning you can skip it, retry it, or modify the return value.

**Q: How does Spring decide which proxy type to use?**
> JDK dynamic proxy if the bean implements at least one interface; CGLIB proxy otherwise. You can force CGLIB with `@EnableAspectJAutoProxy(proxyTargetClass = true)`.

**Q: Why use a custom annotation instead of matching by package/class name?**
> A package-level pointcut like `execution(* request.services..*(..))` is fragile — adding a class or moving a package breaks it silently. An explicit `@RateLimited` is self-documenting and opt-in, making intent clear and changes safe.
***