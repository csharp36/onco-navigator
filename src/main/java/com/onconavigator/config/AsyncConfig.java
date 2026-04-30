package com.onconavigator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's asynchronous method execution support.
 *
 * <p>Required for {@link com.onconavigator.service.AuditService#logAccess} to run
 * asynchronously. Async audit writes ensure that audit logging does not add latency
 * to API responses — HIPAA audit requirements must not degrade clinical workflow performance.
 *
 * <p>The default {@code SimpleAsyncTaskExecutor} is used here. For production, consider
 * configuring a bounded thread pool to avoid unbounded task queue growth under high load.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // Spring Boot's default SimpleAsyncTaskExecutor is sufficient for audit log writes.
    // A custom Executor bean can be added here if throughput tuning is needed.
}
