package request.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterConfig {

    /** Maximum number of tokens the bucket can hold. */
    private long capacity;

    /** Number of tokens added to the bucket each refill interval. */
    private long refillTokens;

    /** Interval in seconds between token refills. */
    private long refillIntervalSeconds;
}
