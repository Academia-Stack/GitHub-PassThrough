# Spring Best Practices

---

## Constructor Injection vs Field Injection

### What are they?

**Field Injection** — Spring uses reflection to set the field directly after instantiation.

```java
@Service
public class MyService {

    @Autowired
    private MyRepository repo;  // Spring sets this via reflection
}
```

**Constructor Injection** — Dependencies are provided through the constructor. Spring calls the constructor with the resolved beans.

```java
@Service
public class MyService {

    private final MyRepository repo;

    public MyService(MyRepository repo) {  // Spring calls this
        this.repo = repo;
    }
}
```

> If a class has exactly one constructor, `@Autowired` is optional — Spring injects automatically (Spring 4.3+).

---

### Why Constructor Injection is Preferred

#### 1. Allows `final` fields

Constructor injection is the **only way** to declare injected dependencies as `final`. This means the dependency:
- Is guaranteed to be set before the object is used
- Cannot be accidentally reassigned after construction
- Makes the class **immutable by design**

```java
// Field injection — cannot be final
@Autowired
private MyRepository repo;

// Constructor injection — can be final (and should be)
private final MyRepository repo;
```

#### 2. Fail-fast at application startup

With constructor injection, if a required bean is missing or ambiguous, the application **fails immediately on startup** with a clear error. You never reach production with a missing dependency.

With field injection, the field is `null` until the method is first called — which may not happen until a specific endpoint is hit, potentially in production.

#### 3. Works without a Spring container (plain unit tests)

Constructor injection lets you instantiate the class with a plain `new` in a unit test — no Spring context, no `@SpringBootTest`, no mocking framework required.

```java
// Constructor injection — testable anywhere
MyRepository mockRepo = mock(MyRepository.class);
MyService service = new MyService(mockRepo);  // works perfectly

// Field injection — this leaves repo as null
MyService service = new MyService();  // repo is null, test will fail
```

This makes your code truly unit-testable and decoupled from the framework.

#### 4. Makes dependencies explicit and visible

A constructor listing five parameters is a clear signal that a class has too many responsibilities. With field injection, you can keep adding `@Autowired` fields without noticing the class is growing too large.

Constructor injection acts as a natural **code smell detector** for the Single Responsibility Principle.

#### 5. Detects circular dependencies early

With constructor injection, Spring detects circular dependencies (A → B → A) at startup and throws a `BeanCurrentlyInCreationException`.

With field injection, circular dependencies are resolved silently using partially-constructed proxies — masking a design problem that only surfaces at runtime.

#### 6. Framework-agnostic

A class using constructor injection has no Spring annotations on its fields at all. The class itself is a plain Java object (POJO). This makes it portable, easier to read, and independent of Spring's DI mechanism.

---

### Summary Table

| | Constructor Injection | Field Injection |
|---|---|---|
| `final` fields | Yes | No |
| Fails at startup if dep missing | Yes | No — fails at first call |
| Plain unit testing (no Spring) | Yes | No — field stays `null` |
| Detects circular deps at startup | Yes | No |
| Reveals bloated classes | Yes | No |
| Boilerplate | Slightly more | Less |
| Spring's official recommendation | **Yes** | No (discouraged since Spring 4) |

---

### When Field Injection is Acceptable

The only widely accepted exception is **test classes** themselves (not production code):

```java
@SpringBootTest
class MyServiceTest {

    @Autowired              // acceptable in tests
    private MyService service;
}
```

In test classes the tradeoffs above don't apply — the class is never reused, never instantiated manually, and Spring manages the lifecycle anyway.

---

### Interview Answer (1-liner)

> *"Constructor injection is preferred because it allows `final` fields, enables plain unit testing without a Spring context, fails fast at startup if dependencies are missing, and makes the class's dependencies explicit — all things field injection cannot guarantee."*
***