package request.ratelimiter.impl;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import request.constants.Messages;
import request.exception.RateLimitExceededException;
import request.ratelimiter.interfaces.IGitHubRateLimiter;

// Resilience4j-backed rate limiter — active only in the prod profile.
// The "github" instance is configured via application-prod.yml under
// resilience4j.ratelimiter.instances.github.
@Slf4j
@Component
@Profile("prod")
public class Resilience4jRateLimiterAdapter implements IGitHubRateLimiter {

    private final RateLimiter rateLimiter;

    public Resilience4jRateLimiterAdapter(RateLimiterRegistry registry) {
        this.rateLimiter = registry.rateLimiter("github");
    }

    @Override
    public void acquire() {
        if (!rateLimiter.acquirePermission()) {
            log.warn(Messages.RATE_LIMITER_LIMIT_EXCEEDED, rateLimiter.getName(), "Resilience4j");
            throw new RateLimitExceededException(Messages.GITHUB_RATE_LIMIT_MESSAGE);
        }
        log.debug(Messages.RATE_LIMITER_PERMIT_ACQUIRED, rateLimiter.getName(), "Resilience4j");
    }
}
