package com.onconavigator.web;

import com.onconavigator.service.AlertService;
import com.onconavigator.web.dto.AlertResponse;
import com.onconavigator.web.dto.ResolveAlertRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for alert management — the nurse navigator's primary action queue.
 *
 * <p>Alerts are surfaced by the Temporal pathway monitoring workflow when patients
 * miss care events, have delayed events, or events occur out of order.
 *
 * <p>Access:
 * <ul>
 *   <li>List and resolve alerts: {@code NURSE_NAVIGATOR} or {@code ADMIN} — alert triage
 *       is the nurse navigator's core workflow</li>
 *   <li>Alert count: any authenticated user — all roles need the sidebar badge count</li>
 * </ul>
 *
 * <p>RBAC uses {@code hasRole('NURSE_NAVIGATOR')} — Spring Security prepends ROLE_
 * automatically. Never use {@code hasRole('ROLE_NURSE_NAVIGATOR')} (double prefix).
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * Returns all open alerts ordered by clinical severity.
     *
     * <p>Ordering: OVERDUE (DELAYED_EVENT) first, MISSING second, OUT OF ORDER third.
     * Within each tier, oldest alerts appear first. This is the nurse navigator's
     * primary action queue.
     *
     * @return severity-ordered list of open alerts as response DTOs
     */
    @GetMapping
    @PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    public List<AlertResponse> listOpenAlerts() {
        return alertService.getOpenAlerts();
    }

    /**
     * Returns the count of currently open alerts.
     *
     * <p>Lightweight endpoint for sidebar badge polling (D-11). Returns
     * {@code {"count": N}} for easy frontend consumption. Accessible to all
     * authenticated users — all roles display the badge.
     *
     * @return JSON object with "count" key and open alert count value
     */
    @GetMapping("/count")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> getOpenAlertCount() {
        return Map.of("count", alertService.countOpenAlerts());
    }

    /**
     * Resolves an alert with resolution notes and records the acting nurse.
     *
     * <p>Sets the alert status to RESOLVED, records the resolution timestamp,
     * actor UUID, and notes in the alerts table (and the Envers audit trail).
     *
     * @param id      the alert UUID to resolve
     * @param request resolution request with notes documenting the corrective action
     * @param jwt     the authenticated user's JWT (for actor identity)
     */
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolveAlert(@PathVariable UUID id,
                             @Valid @RequestBody ResolveAlertRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        alertService.resolveAlert(id, request, actorId);
    }
}
