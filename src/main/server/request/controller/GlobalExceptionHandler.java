package request.controller;

import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import request.dto.server.ApiErrorResponse;
import request.dto.server.ExceptionDTO;
import request.exception.RateLimitExceededException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Feign client errors (prod profile).
    // FeignException.status() returns -1 for connection-level failures (e.g. host unreachable)
    // before any HTTP response is received; treat those as 503 Service Unavailable.
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ExceptionDTO<ApiErrorResponse>> handleFeignException(
            FeignException ex, HttpServletRequest request) {
        int feignStatus = ex.status();
        HttpStatus status = feignStatus > 0
                ? HttpStatus.resolve(feignStatus)
                : HttpStatus.SERVICE_UNAVAILABLE;
        if (status == null) status = HttpStatus.BAD_GATEWAY;
        return buildResponse(status, ex.getMessage(), request.getRequestURI());
    }

    // RestClient 4xx errors (dev profile — restclient).
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ExceptionDTO<ApiErrorResponse>> handleHttpClientError(
            HttpClientErrorException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.BAD_REQUEST;
        return buildResponse(status, ex.getStatusText(), request.getRequestURI());
    }

    // RestClient 5xx errors (dev profile — restclient).
    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ExceptionDTO<ApiErrorResponse>> handleHttpServerError(
            HttpServerErrorException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return buildResponse(status, ex.getStatusText(), request.getRequestURI());
    }

    // RuntimeException wrapping java HttpClient I/O failures (dev profile — httpclient).
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ExceptionDTO<ApiErrorResponse>> handleRuntimeException(
            RuntimeException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request.getRequestURI());
    }

    // Rate limit exceeded — returned as HTTP 429 Too Many Requests.
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ExceptionDTO<ApiErrorResponse>> handleRateLimitExceeded(
            RateLimitExceededException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request.getRequestURI());
    }

    // Spring MVC 6+: requested path does not map to any handler.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ExceptionDTO<ApiErrorResponse>> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    // Catch-all — must remain last so more specific handlers take priority.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionDTO<ApiErrorResponse>> handleException(
            Exception ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage(),
                request.getRequestURI());
    }

    private ResponseEntity<ExceptionDTO<ApiErrorResponse>> buildResponse(
            HttpStatus status, String message, String path) {
        ApiErrorResponse errorDetails = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .build();
        ExceptionDTO<ApiErrorResponse> body = ExceptionDTO.<ApiErrorResponse>builder()
                .data(errorDetails)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
