package request.aop.annotation;

import request.aop.CustomRateLimiterAspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as subject to GitHub API rate limiting.
 * Any method annotated with @RateLimited will have a rate-limit token
 * consumed before execution via {@link CustomRateLimiterAspect}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {}
