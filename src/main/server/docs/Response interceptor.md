# Response Interceptor

## Overview

`ResponseInterceptor` is a Spring `@RestControllerAdvice` component that implements `ResponseBodyAdvice<Object>`. Its purpose is to automatically wrap every successful controller response in a uniform **envelope** so that API consumers always receive a consistent JSON (or XML) structure.

### Location

```
request/interceptor/ResponseInterceptor.java
```

---

## Why It Exists

Without a response interceptor, each controller method would have to manually wrap its return value in a `ResponseDTO`. The interceptor removes that boilerplate — controllers simply return their domain objects and the envelope is applied transparently.

### Uniform Response Envelope

| Scenario | Envelope DTO | `success` field |
|---|---|---|
| Successful response | `ResponseDTO<T>` | `true` |
| Exception / error | `ExceptionDTO<T>` | `false` |

**Successful response example:**

```json
{
  "success": true,
  "data": { ... }
}
```

**Error response example (produced by `GlobalExceptionHandler`):**

```json
{
  "success": false,
  "data": {
    "timestamp": "2026-08-01T12:00:00",
    "status": 404,
    "error": "Not Found",
    "message": "No static resource api/invalid.",
    "path": "/api/invalid"
  }
}
```

---

## How It Works

`ResponseInterceptor` implements two methods from the `ResponseBodyAdvice<Object>` interface:

### `supports(MethodParameter, Class)`

```java
@Override
public boolean supports(MethodParameter returnType,
        Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
}
```

This method acts as a **filter/gate** that tells Spring whether the interceptor should be applied to a given controller method.

- **`returnType`** — metadata about the controller method's return type (class, annotations, generic info, etc.).
- **`converterType`** — the `HttpMessageConverter` Spring has selected to serialize the response (e.g., `MappingJackson2HttpMessageConverter` for JSON).

By returning **`true` unconditionally**, the interceptor applies to *every* controller response regardless of return type or converter. If selective wrapping were needed (e.g., skipping `byte[]` or `Resource` responses), this method would contain the filtering logic and return `false` for those cases.

### `beforeBodyWrite(...)`

```java
@Override
public Object beforeBodyWrite(Object body,
        MethodParameter returnType,
        MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType,
        ServerHttpRequest request,
        ServerHttpResponse response) {

    if (body instanceof ResponseDTO<?> || body instanceof ExceptionDTO<?>)
        return body;

    return ResponseDTO.builder().data(body).build();
}
```

This method is called **after** the controller returns but **before** the response body is serialized and written to the HTTP output stream. It performs the actual wrapping:

1. **Double-wrap guard** — If the body is already a `ResponseDTO` or `ExceptionDTO`, it is returned as-is. This prevents responses from the `GlobalExceptionHandler` (which already return `ExceptionDTO`) from being wrapped a second time.
2. **Wrap** — Otherwise, the body is placed inside a new `ResponseDTO`, setting `success = true` automatically.

---

## Interaction with `GlobalExceptionHandler`

Both `ResponseInterceptor` and `GlobalExceptionHandler` are annotated with `@RestControllerAdvice`, but they serve complementary roles:

| Component | Role |
|---|---|
| `GlobalExceptionHandler` | Catches exceptions, builds an `ExceptionDTO<ApiErrorResponse>`, and returns it with the appropriate HTTP status code. |
| `ResponseInterceptor` | Wraps **all** outgoing response bodies in `ResponseDTO` — unless the body is already an envelope type. |

---

## Envelope DTOs

### `ResponseDTO<T>`

```java
@Data @Builder
@JacksonXmlRootElement(localName = "response")
public class ResponseDTO<T> {
    private final boolean success = true;
    private T data;
}
```

- `success` is hard-coded to `true`.
- `data` holds the actual payload.
- Supports both JSON and XML serialization (via `@JacksonXmlRootElement`).

### `ExceptionDTO<T>`

```java
@Data @Builder
@JacksonXmlRootElement(localName = "error")
public class ExceptionDTO<T> {
    private final boolean success = false;
    private T data;
}
```

- `success` is hard-coded to `false`.
- `data` typically holds an `ApiErrorResponse` containing `timestamp`, `status`, `error`, `message`, and `path`.

---

## Key Takeaways

- Controllers don't need to manually wrap responses — the interceptor does it automatically.
- The `supports` method returning `true` means **no controller is excluded** from wrapping.
- The `instanceof` check in `beforeBodyWrite` ensures error responses from `GlobalExceptionHandler` are never double-wrapped.
- Both JSON and XML content types are supported via Jackson annotations on the DTOs.
***