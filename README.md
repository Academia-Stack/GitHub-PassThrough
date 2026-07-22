# GitHub API Request Service

A Spring Boot 4.1 application that proxies the [GitHub REST API](https://api.github.com), demonstrating three different HTTP client implementations switchable by Spring Profile and configuration property.

---

## Tech Stack

| Concern | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 |
| HTTP Clients | Spring RestClient · Java `HttpClient` · Spring Cloud OpenFeign |
| Serialization | Jackson (JSON + XML) |
| Prod client infra | Spring Cloud 2025.0 (OpenFeign) |
| Rate Limiting | Spring AOP · Custom token bucket (dev) · Resilience4j (prod) |
| Logging | SLF4J via Lombok `@Slf4j` |

---

## Architecture Overview

The service defines a single `IGitHubService` contract. Three implementations exist; exactly one is registered as a Spring bean depending on the active profile and a configuration property:

```
Profile = dev
  github.client = restclient  →  GitHubRestClientService  (Spring RestClient)
  github.client = httpclient  →  GitHubHttpClientService   (Java HttpClient)

Profile = prod
  (always)                    →  GitHubFeignClient         (Spring Cloud Feign)
```

No `@Primary` or `@Qualifier` is needed — the combination of `@Profile` and `@ConditionalOnProperty` ensures only one bean is ever active per environment.

### Rate Limiting

Rate limiting is applied as a **cross-cutting concern via Spring AOP**, keeping it completely separate from service business logic:

```
Profile = dev  →  @RateLimited annotation + CustomRateLimiterAspect
                      └─ GitHubRateLimiter (token bucket, AtomicLong CAS)

Profile = prod →  FeignConfig RequestInterceptor
                      └─ Resilience4jRateLimiterAdapter (Resilience4j AtomicRateLimiter)
```

In dev, service methods are annotated with `@RateLimited`; the aspect fires `acquire()` before execution. In prod, a Feign `RequestInterceptor` calls `acquire()` before every outbound HTTP request. Both paths throw `RateLimitExceededException` → **HTTP 429** on exhaustion.

---

### Logging

All logging uses **SLF4J**, added to classes via Lombok's `@Slf4j` annotation. Log message templates are centralised in `Messages.java`.

| Location | Level | Message |
|---|---|---|
| `GitHubRestClientService` | `INFO` | `Data from Rest client: {response}` |
| `GitHubHttpClientService` | `INFO` | `Data from Http client: {response}` |
| `GitHubFeignService` | `INFO` | `Data from Feign client: {response}` |
| `GitHubHttpClientService` (error) | `ERROR` | Failed fetch with exception |
| `GitHubRateLimiter` | `DEBUG` / `WARN` | Permit acquired / limit exceeded with instance name and implementation |
| `Resilience4jRateLimiterAdapter` | `DEBUG` / `WARN` | Permit acquired / limit exceeded with instance name and implementation |

Rate limiter messages include both the instance name (e.g. `github`) and the implementation (e.g. `Custom RateLimiter` or `Resilience4j`) so log output clearly identifies which limiter fired.

---

## Running the Application

### Default (dev, RestClient)
```bash
./mvnw spring-boot:run
```
Default profile is `dev` with `github.client=restclient`.

### Dev with HttpClient
Set in `src/main/resources/application-dev.yml`:
```yaml
github:
  client: httpclient
```
Then run normally.

### Production (Feign)
```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```
Feign is always used in `prod` regardless of `github.client`.
---

## Configuration Reference

| Property | File | Description |
|---|---|---|
| `github.base-url` | `application.yaml` | GitHub API base URL |
| `spring.profiles.active` | `application.yaml` | Active profile (`dev` by default) |
| `github.client` | `application-dev.yml` | Dev client selector: `restclient` \| `httpclient` |
| `github.retry.max-attempts` | `application-prod.yml` | Feign retry: total attempts (default `3`) |
| `github.retry.period` | `application-prod.yml` | Feign retry: initial backoff ms (default `100`) |
| `github.retry.max-period` | `application-prod.yml` | Feign retry: max backoff ms (default `1000`) |
| `rate-limiter.capacity` | `application-dev.yml` | Token bucket max size (default `60`) |
| `rate-limiter.refill-tokens` | `application-dev.yml` | Tokens restored per interval (default `60`) |
| `rate-limiter.refill-interval-seconds` | `application-dev.yml` | Refill interval in seconds (default `3600`) |
| `resilience4j.ratelimiter.instances.github.*` | `application-prod.yml` | Resilience4j rate limiter for the prod Feign client |

---

## API Endpoints

Base path: `/github`

| Method | Path | Produces | Description |
|---|---|---|---|
| `GET` | `/github/users` | `application/json` | All GitHub users (JSON) |
| `GET` | `/github/users` | `application/xml` | All GitHub users (XML) |
| `GET` | `/github/users/{username}` | `application/json`, `application/xml` | Single user by username |

### Response shape

Every successful response is wrapped in a `ResponseDTO` envelope:
```json
{
  "success": true,
  "data": { ... }
}
```

Every error is wrapped in an `ExceptionDTO` envelope:
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

---

## Project Structure

```
src/main/server/request/
  aop/
    annotation/
      RateLimited.java                    — @RateLimited method annotation
    CustomRateLimiterAspect.java          — Before-advice: calls acquire() on @RateLimited methods
  config/
    HttpClientConfig.java                 — Java HttpClient bean (dev)
    FeignConfig.java                      — Feign logger, interceptors, retryer + rate-limit interceptor (prod)
    RateLimiterConfig.java                — @ConfigurationProperties for rate-limiter.*
    RestClientConfig.java                 — RestClient bean (dev)
  constants/
    Messages.java                         — Shared message strings
  controller/
    GitHubController.java                 — REST endpoints
    GlobalExceptionHandler.java           — Maps exceptions → HTTP status codes
  dto/
    github/                               — GitHub API payload DTOs (GitHubUser, GitHubUserList)
    server/                               — Envelope DTOs (ResponseDTO, ExceptionDTO, ApiErrorResponse)
  exception/
    RateLimitExceededException.java       — Thrown when bucket is empty → HTTP 429
  ratelimiter/
    interfaces/
      IGitHubRateLimiter.java             — Rate limiter contract
    impl/
      GitHubRateLimiter.java              — Token bucket implementation (dev)
      Resilience4jRateLimiterAdapter.java — Resilience4j wrapper (prod)
  services/
    interfaces/
      IGitHubService.java                 — Service contract
      GitHubFeignClient.java              — Feign HTTP client interface (prod)
    impl/
      GitHubRestClientService.java        — RestClient implementation (dev)
      GitHubHttpClientService.java        — HttpClient implementation (dev)
      GitHubFeignService.java             — Feign service wrapper with logging (prod)

src/main/resources/
  application.yaml                        — Shared config + default profile
  application-dev.yml                     — Dev: client selector + rate limiter config
  application-prod.yml                    — Prod: Feign retry tuning + Resilience4j rate limiter
```

---

## Further Reading

For in-depth implementation details — bean selection mechanics, value binding internals, `@EnableFeignClients` behaviour, response envelope design, and exception status method differences — see [`src/main/server/docs/Request service.md`](src/main/server/docs/Request%20service.md).

For the rate limiter — token bucket algorithm, thread-safety via CAS loops, refill logic, and the dev/prod split between the custom implementation and Resilience4j — see [`src/main/server/docs/Rate limiter.md`](src/main/server/docs/Rate%20limiter.md).

For Spring core concepts used throughout the project — dependency injection, IoC container, bean stereotypes, and wiring mechanics — see [`src/main/server/docs/Dependency injection.md`](src/main/server/docs/Dependency%20injection.md).

For Aspect-Oriented Programming — what AOP is, how `@Aspect`, pointcuts, and before-advice work, and how `CustomRateLimiterAspect` applies it — see [`src/main/server/docs/Spring AOP.md`](src/main/server/docs/Spring%20AOP.md).

For Spring best practices applied in this project — constructor injection, immutability, and related patterns — see [`src/main/server/docs/Best practices.md`](src/main/server/docs/Best%20practices.md).
***