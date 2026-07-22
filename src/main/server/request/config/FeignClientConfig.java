package request.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.Retryer;
import request.ratelimiter.interfaces.IGitHubRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("prod")
// @EnableFeignClients activates Spring Cloud OpenFeign's infrastructure. It registers a
// FeignClientsRegistrar that scans the given basePackages for @FeignClient interfaces and
// generates JDK proxy implementations for them at runtime — no manual HTTP code needed.
//
// It is placed here on FeignClientConfig rather than on RequestApplication because this class
// is guarded by @Profile("prod"). That means the Feign infrastructure and its client beans
// are only registered when the prod profile is active, preventing bean conflicts with the
// RestClient/HttpClient implementations used in the dev profile.
//
// basePackages must be specified explicitly because FeignClientConfig lives in
// "launchpad.request.config", a different package branch from GitHubFeignClient which
// lives in "launchpad.request.services.interfaces". Without it, the default scan root
// (this class's own package) would never reach the @FeignClient interface.
@EnableFeignClients(basePackages = "request.services.interfaces")
public class FeignClientConfig {
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public RequestInterceptor githubRequestInterceptor() {
        return requestTemplate -> requestTemplate.header("Accept", "application/json");
    }

    // Retryer.Default uses exponential backoff: starts at 'period' ms, grows 1.5x each attempt
    // up to 'maxPeriod' ms, for a total of 'maxAttempts' attempts (including the first call).
    // Feign's default is Retryer.NEVER_RETRY — this bean overrides that behaviour.
    @Bean
    public Retryer retryer(
            @Value("${github.retry.period:100}") long period,
            @Value("${github.retry.max-period:1000}") long maxPeriod,
            @Value("${github.retry.max-attempts:3}") int maxAttempts) {
        return new Retryer.Default(period, maxPeriod, maxAttempts);
    }

    @Bean
    public RequestInterceptor rateLimitInterceptor(IGitHubRateLimiter rateLimiter) {
        return requestTemplate -> rateLimiter.acquire();
    }
}
