package request.interceptor;

import request.dto.server.ExceptionDTO;
import request.dto.server.ResponseDTO;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Intercepts all outgoing controller responses and wraps them inside
 * {@link ResponseDTO} so every successful response follows a uniform
 * {@code { "success": true, "data": ... }} envelope.
 *
 * Responses that are already wrapped — such as {@link ExceptionDTO}
 * produced by the {@code GlobalExceptionHandler} — are passed through
 * untouched.
 */
@RestControllerAdvice
public class ResponseInterceptor implements ResponseBodyAdvice<Object> {

    /**
     * Apply to every controller method.
     */
    @Override
    public boolean supports(MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /**
     * Wrap the body in a {@link ResponseDTO} unless it is already an envelope
     * type ({@link ResponseDTO} or {@link ExceptionDTO}).
     */
    @Override
    public Object beforeBodyWrite(Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        // Don't double-wrap responses that are already enveloped
        if (body instanceof ResponseDTO<?> || body instanceof ExceptionDTO<?>)
            return body;

        return ResponseDTO.builder().data(body).build();
    }
}
