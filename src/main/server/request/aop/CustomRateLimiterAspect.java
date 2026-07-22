package request.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import request.ratelimiter.interfaces.IGitHubRateLimiter;

/**
 * AOP aspect that enforces rate limiting for all methods annotated with
 * {@link request.aop.annotation.RateLimited}.
 *
 * <p>This keeps rate-limiting logic as a cross-cutting concern, completely
 * separate from business logic in service classes.</p>
 */
@Aspect
@Component
public class CustomRateLimiterAspect {
    @Autowired
    private IGitHubRateLimiter rateLimiter;

    @Before("@annotation(request.aop.annotation.RateLimited)")
    public void applyRateLimit() {
        rateLimiter.acquire();
    }
}
