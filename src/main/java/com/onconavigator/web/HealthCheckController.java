package com.onconavigator.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight health check controller used by Docker HEALTHCHECK and ECS load balancer probes.
 *
 * <p>Returns a minimal JSON response indicating the JVM and Spring context are alive.
 * This is intentionally simple — it does NOT check database connectivity or downstream
 * service health because:
 * <ul>
 *   <li>Docker Compose health checks on dependent services (PostgreSQL, Temporal) already
 *       guard against cascading failures</li>
 *   <li>Deep health checks that call the DB can introduce latency and connection pressure
 *       during high-frequency probe intervals</li>
 *   <li>If the application context is up and responding, the JVM is healthy for routing</li>
 * </ul>
 *
 * <p>For detailed composite health (DB, disk, temporal), use the Spring Actuator endpoint:
 * {@code GET /actuator/health} (requires {@code ROLE_ADMIN} to see details).
 *
 * <p>Security: {@code /health} is configured {@code permitAll()} in
 * {@link com.onconavigator.security.SecurityConfig}. Docker's {@code CMD wget} cannot
 * send an Authorization header, so the endpoint must be public.
 */
@RestController
public class HealthCheckController {

    /**
     * Returns a 200 OK with {@code {"status": "UP"}} when the Spring application context
     * is alive and serving requests.
     *
     * <p>Used by the Docker {@code HEALTHCHECK} instruction and ECS load balancer probes.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
