# GitHub API Request Service — In-Depth System Design

> High-level overview, endpoints, and running instructions are in [`README.md`](../../../../../README.md).  
> This document covers implementation internals, design decisions, and Spring/Feign-specific behaviour.

---

## 1. HTTP Client Comparison

### RestClient (Spring 6+)
Spring's modern synchronous HTTP client introduced in Spring 6. Fluent builder API that replaces the older `RestTemplate`. Blocking — the calling thread waits for the response. Ships with Spring Web; no extra dependency needed.

```java
githubRestClient.get()
    .uri("/users/{username}", username)
    .retrieve()
    .body(GitHubUser.class);
```

**Best for:** Standalone Spring Boot apps calling external APIs where reactive behaviour is not required.

---

### Java HttpClient (Java 11 built-in)
`java.net.http.HttpClient` is part of the JDK. No Spring dependency at all — works in any Java 11+ application. Supports both synchronous (`send()`) and asynchronous (`sendAsync()`) modes. Requires manual JSON deserialization (e.g., via `ObjectMapper`).

```java
HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
return objectMapper.readValue(response.body(), GitHubUser.class);
```

**Best for:** Projects that need to minimise Spring dependencies, or when fine-grained control over the raw HTTP lifecycle is needed.

---

### WebClient (Spring WebFlux)
Spring's reactive, non-blocking HTTP client. Returns `Mono<T>` / `Flux<T>` instead of the value directly. Requires the `spring-boot-starter-webflux` dependency and a reactive runtime (Netty). Ideal when the entire request pipeline is reactive.

```java
webClient.get().uri("/users")
    .retrieve()
    .bodyToFlux(GitHubUser.class); // returns Flux<GitHubUser>
```

**Best for:** Reactive microservices, high-concurrency scenarios, applications already using Project Reactor.

---

### FeignClient (Spring Cloud OpenFeign)
Declarative HTTP client — you write only an annotated interface; Spring Cloud generates the proxy implementation at runtime. Requires `spring-cloud-starter-openfeign`. Integrates natively with Spring Cloud's service discovery (Eureka/Consul), load balancing, and circuit breakers.

```java
@FeignClient(name = "github", url = "${github.base-url}")
public interface GitHubFeignClient extends IGitHubService {
    @GetMapping("/users/{username}")
    GitHubUser getUserByUsername(@PathVariable("username") String username);
}
```

**Best for:** Microservice-to-microservice communication where service discovery, load balancing, and retry are required with minimal boilerplate. In standalone apps its main benefit is ergonomics (zero implementation code).

---

### Quick Comparison

| | RestClient | Java HttpClient | WebClient | FeignClient |
|---|---|---|---|---|
| Style | Fluent builder | Fluent builder | Reactive builder | Declarative interface |
| Blocking | Yes | Yes / No | No (reactive) | Yes |
| Extra dependency | None | None (JDK) | spring-webflux | spring-cloud-openfeign |
| Service discovery | No | No | No | Yes (Spring Cloud) |
| Load balancing | No | No | No | Yes (Spring Cloud) |
| Built-in retry | No | No | No | Yes (`Retryer` bean) |
| JSON deserialization | Auto | Manual | Auto | Auto |

---

## 2. Bean Selection by Environment (Spring Profiles)

This project uses Spring Profiles combined with `@ConditionalOnProperty` so that exactly one `IGitHubService` bean is registered per environment — no `@Primary` or `@Qualifier` needed on the injection site.

### Profile structure

```
application.yaml          ← shared config, sets default profile to 'dev'
application-dev.yml       ← dev overrides (github.client toggles the implementation)
application-prod.yml      ← prod overrides (retry tuning for Feign)
```

Spring merges these at startup: `application.yaml` is always loaded first, then the active profile's file overrides or adds to it.

### Dev profile — runtime toggle between RestClient and HttpClient

Both dev implementations carry two conditions that must both be true for the bean to register:

| Annotation | Purpose |
|---|---|
| `@Profile("dev")` | Bean is only a candidate when the active profile is `dev` |
| `@ConditionalOnProperty(name="github.client", havingValue="...")` | Bean is only created if the named property has the specified value |

```
github.client = restclient  →  GitHubService (RestClient)      registered
                               GitHubHttpClientService           skipped

github.client = httpclient  →  GitHubService                   skipped
                               GitHubHttpClientService (HttpClient) registered
```

`matchIfMissing = true` on `GitHubService` means RestClient is the default if the property is absent.

To switch client in dev: edit `application-dev.yml`:
```yaml
github:
  client: httpclient   # change to restclient to revert
```

### Prod profile — Feign always active

`FeignConfig` carries `@Profile("prod")` and `@EnableFeignClients`. Because `@EnableFeignClients` is on a profile-gated `@Configuration` class rather than on `RequestApplication`, Feign's bean registration infrastructure **only activates in prod**. In dev, no Feign beans are created at all, which prevents bean ambiguity.

```
Profile = dev   →  FeignConfig never loaded  →  GitHubFeignClient never registered
Profile = prod  →  FeignConfig loaded        →  GitHubFeignClient registered as IGitHubService
```

To activate prod locally:
```
SPRING_PROFILES_ACTIVE=prod
```
or in `application.yaml`:
```yaml
spring.profiles.active: prod
```

---

## 3. Value Mapping from YAML to Java Components

Spring resolves `@Value` placeholders and `@ConditionalOnProperty` against the merged property sources at startup. Profile-specific files have higher precedence than `application.yaml`.

### Base URL — `RestClientConfig` and `GitHubHttpClientService`

```yaml
# application.yaml
github:
  base-url: https://api.github.com
```

```java
// RestClientConfig.java
@Value("${github.base-url}")
private String baseUrl;

// GitHubHttpClientService.java
public GitHubHttpClientService(..., @Value("${github.base-url}") String baseUrl)
```

### Client selector — conditional bean registration (dev only)

```yaml
# application-dev.yml
github:
  client: restclient   # or httpclient
```

```java
@ConditionalOnProperty(name = "github.client", havingValue = "restclient", matchIfMissing = true)
public class GitHubService ...          // active when github.client=restclient (or absent)

@ConditionalOnProperty(name = "github.client", havingValue = "httpclient")
public class GitHubHttpClientService .. // active when github.client=httpclient
```

### Retry config — `FeignConfig` (prod only)

```yaml
# application-prod.yml
github:
  retry:
    max-attempts: 3
    period: 100
    max-period: 1000
```

```java
// FeignClientConfig.java
@Bean
public Retryer retryer(
    @Value("${github.retry.period:100}") long period,          // fallback: 100ms
    @Value("${github.retry.max-period:1000}") long maxPeriod,  // fallback: 1000ms
    @Value("${github.retry.max-attempts:3}") int maxAttempts)  // fallback: 3
```

The `:` syntax provides a **default value** — if the property is missing (e.g., when running in dev), the fallback is used instead of a startup failure. This means the retry bean is safe to construct in any profile even though the values are only defined in `application-prod.yml`.

`Retryer.Default` applies **exponential backoff** between attempts:

$$\text{wait}_n = \min(\text{period} \times 1.5^{n-1},\ \text{maxPeriod})$$

With defaults: 100ms → 150ms → exception thrown (3 total attempts).

---

## 4. `@EnableFeignClients` — Important Behaviour Notes

- **Not lazy.** It eagerly registers all `@FeignClient` proxy beans when the application context loads. This is why it must not be placed on `RequestApplication` when profiles are in use — doing so would register Feign beans in every profile, causing a `NoUniqueBeanDefinitionException` in dev.

- **Default scan root** is the package of the class that carries the annotation. If `@EnableFeignClients` is on `FeignConfig` (package `launchpad.request.config`) and `GitHubFeignClient` is in `launchpad.request.services.interfaces`, the packages are in different branches — `basePackages` **must** be set explicitly.

- **`configuration` attribute on `@FeignClient`** wires per-client beans (logger level, interceptors, retryer) into that specific client only, not globally. `FeignConfig` is referenced this way in `GitHubFeignClient`.

---

## 5. Uniform Response Envelope (ResponseDTO / ExceptionDTO)

All controller responses and all exceptions are wrapped in a consistent envelope so callers always receive the same top-level shape.

### ResponseDTO\<T\> — success envelope

```java
@Data @Builder
@JacksonXmlRootElement(localName = "response")
public class ResponseDTO<T> {
    private final boolean success = true;  // always true
    private T data;
}
```

Used in every controller method. `T` is the business payload (`GitHubUser`, `List<GitHubUser>`, `GitHubUserList`).

```java
return ResponseEntity.ok(
    ResponseDTO.<GitHubUser>builder()
        .data(githubUserService.getUserByUsername(username))
        .build());
```

```json
{
  "success": true,
  "data": { "login": "octocat", "id": 1, ... }
}
```

### ExceptionDTO\<T\> — error envelope

```java
@Data @Builder
@JacksonXmlRootElement(localName = "error")
public class ExceptionDTO<T> {
    private final boolean success = false;  // always false
    private T data;
}
```

Used in `GlobalExceptionHandler`. `T` is `ApiErrorResponse`, which carries the structured error details.

```java
ExceptionDTO.<ApiErrorResponse>builder()
    .data(ApiErrorResponse.builder()
        .timestamp(LocalDateTime.now())
        .status(status.value())
        .error(status.getReasonPhrase())
        .message(message)
        .path(path)
        .build())
    .build();
```

```json
{
  "success": false,
  "data": {
    "timestamp": "2026-07-21T10:30:00",
    "status": 404,
    "error": "Not Found",
    "message": "...",
    "path": "/github/users/unknown"
  }
}
```

### Why two separate classes instead of one?

`success` is a `final` field with a hardcoded initialiser — it can never be set to the wrong value by accident. Using separate types also makes handler return types self-documenting: `ResponseEntity<ResponseDTO<T>>` clearly signals success; `ResponseEntity<ExceptionDTO<ApiErrorResponse>>` clearly signals failure.

### GlobalExceptionHandler — handler coverage

| Handler | Exception type | Triggered by |
|---|---|---|
| `handleFeignException` | `FeignException` | Feign HTTP/connection errors (prod) |
| `handleHttpClientError` | `HttpClientErrorException` | RestClient 4xx responses (dev, restclient) |
| `handleHttpServerError` | `HttpServerErrorException` | RestClient 5xx responses (dev, restclient) |
| `handleRuntimeException` | `RuntimeException` | HttpClient I/O failures wrapped in service (dev, httpclient) |
| `handleNoResourceFound` | `NoResourceFoundException` | Unmapped URL paths (Spring MVC 6+) |
| `handleException` | `Exception` | Catch-all, lowest priority |

Spring resolves `@ExceptionHandler` methods from most-specific to least-specific type, so `FeignException` and `HttpClientErrorException` always win over `RuntimeException` and `Exception`.

---

## 6. Exception Status Methods

Three different mechanisms are used across the handlers to extract and resolve HTTP status codes.

### `ex.status()` — `FeignException` (Feign library)

Returns a **raw `int`**. Returns `-1` when no HTTP response was received at all (e.g., connection refused, DNS failure, timeout before any response). `-1` is not a valid HTTP status code and must be handled explicitly.

```java
int feignStatus = ex.status();   // 404, 503, -1, etc.
```

### `ex.getStatusCode()` — `HttpClientErrorException` / `HttpServerErrorException` (Spring)

Returns `org.springframework.http.HttpStatusCode` — an **interface** introduced in Spring 6 to accommodate non-standard status codes (e.g. `999`) that don't exist in the `HttpStatus` enum. Call `.value()` to get the raw int.

```java
HttpStatusCode code = ex.getStatusCode();  // e.g. HttpStatus.NOT_FOUND
int raw = ex.getStatusCode().value();      // → 404
```

Spring only throws these exceptions after receiving an actual HTTP response, so the status code is always a real positive integer — no `-1` risk.

### `HttpStatus.resolve(int statusCode)` — enum lookup

Converts a raw `int` into the matching `HttpStatus` enum constant. Returns **`null`** if the integer does not correspond to any known standard status code.

```java
HttpStatus.resolve(404)  // → HttpStatus.NOT_FOUND
HttpStatus.resolve(200)  // → HttpStatus.OK
HttpStatus.resolve(999)  // → null  (non-standard)
HttpStatus.resolve(-1)   // → null  (Feign connection failure sentinel)
```

Because it can return `null`, a fallback is always needed:

```java
HttpStatus status = HttpStatus.resolve(feignStatus);
if (status == null) status = HttpStatus.BAD_GATEWAY;
```

### Side-by-side summary

| | `ex.status()` | `ex.getStatusCode()` |
|---|---|---|
| Exception type | `FeignException` (Feign) | Spring `HttpClientErrorException` / `HttpServerErrorException` |
| Return type | `int` | `HttpStatusCode` (Spring interface) |
| Can be -1? | Yes — no response received | No — always a real HTTP response |
| Get raw int | Already an int | `.value()` |

---

## 7. Project Structure Reference

```
config/
  RestClientConfig.java         — RestClient bean, reads github.base-url
  HttpClientConfig.java         — java.net.http.HttpClient bean
  FeignConfig.java              — @Profile("prod"), @EnableFeignClients, Retryer + interceptor beans

controller/
  GitHubController.java         — endpoints, wraps all responses in ResponseDTO<T>
  GlobalExceptionHandler.java   — @RestControllerAdvice, wraps all errors in ExceptionDTO<ApiErrorResponse>

dto/
  github/
    GitHubUser.java             — GitHub user payload
    GitHubUserList.java         — XML wrapper for list of users
  server/
    ResponseDTO.java            — success envelope: success=true, data=T
    ExceptionDTO.java           — error envelope: success=false, data=T
    ApiErrorResponse.java       — structured error details (timestamp, status, error, message, path)

services/
  interfaces/
    IGitHubService.java         — contract: getAllUsers(), getUserByUsername()
    GitHubFeignClient.java      — @FeignClient, prod implementation (proxy-generated)
  impl/
    GitHubService.java          — @Profile("dev") + restclient condition
    GitHubHttpClientService.java — @Profile("dev") + httpclient condition

resources/
  application.yaml              — shared: base-url, default profile = dev
  application-dev.yml           — github.client toggle (restclient | httpclient)
  application-prod.yml          — retry tuning (max-attempts, period, max-period)
```
***