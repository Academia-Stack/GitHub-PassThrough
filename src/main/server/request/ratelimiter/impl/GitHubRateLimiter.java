package request.ratelimiter.impl;

import request.config.RateLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import request.constants.Messages;
import request.exception.RateLimitExceededException;
import request.ratelimiter.interfaces.IGitHubRateLimiter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe token bucket rate limiter for GitHub API calls.
 *
 * <p>
 * Tokens are refilled periodically based on {@link RateLimiterConfig}.
 * Calling {@link #acquire()} consumes one token; if none are available a
 * {@link RateLimitExceededException} is thrown immediately (non-blocking).
 */
@Slf4j
@Component
@Profile("dev")
public class GitHubRateLimiter implements IGitHubRateLimiter {
    private final long capacity;
    private final long refillTokens;
    private final long refillIntervalNanos;

    private final AtomicLong availableTokens;
    private final AtomicLong lastRefillNanos;

    public GitHubRateLimiter(RateLimiterConfig props) {
        this.capacity = props.getCapacity();
        this.refillTokens = props.getRefillTokens();
        this.refillIntervalNanos = TimeUnit.SECONDS.toNanos(props.getRefillIntervalSeconds());
        this.availableTokens = new AtomicLong(props.getCapacity());
        this.lastRefillNanos = new AtomicLong(System.nanoTime());
    }

    /**
     * Attempts to acquire one token.
     *
     * @throws RateLimitExceededException if no tokens are currently available
     */
    public void acquire() {
        refill();
        long current;
        do {
            current = availableTokens.get();
            if (current <= 0) {
                log.warn(Messages.RATE_LIMITER_LIMIT_EXCEEDED, "github", "Custom RateLimiter");
                throw new RateLimitExceededException(Messages.GITHUB_RATE_LIMIT_MESSAGE);
            }
        } while (!availableTokens.compareAndSet(current, current - 1));
        log.debug(Messages.RATE_LIMITER_PERMIT_ACQUIRED, "github", "Custom RateLimiter");
    }

    /** Returns the number of tokens currently available (snapshot). */
    public long availableTokens() {
        refill();
        return availableTokens.get();
    }

    private void refill() {
        long now = System.nanoTime();
        long last = lastRefillNanos.get();
        long elapsed = now - last;

        if (elapsed < refillIntervalNanos)
            return;

        long periods = elapsed / refillIntervalNanos;
        long newLast = last + periods * refillIntervalNanos;

        // Only one thread performs the refill for a given period.
        if (lastRefillNanos.compareAndSet(last, newLast)) {
            long tokensToAdd = periods * refillTokens;
            availableTokens.updateAndGet(t -> Math.min(capacity, t + tokensToAdd));
        }
    }
}

/*
CAS - compareAndSet
Atomically sets the value to newValue if the current value == expectedValue,
with memory effects as specified by VarHandle.compareAndSet.

Parameters:
    expectedValue: the expected value
    newValue: the new value
Returns:
    true if successful.
    false return indicates that the actual value was not equal to the expected value.
*/
