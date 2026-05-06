package com.onconavigator.web;

import com.onconavigator.domain.NotificationPreference;
import com.onconavigator.notification.NotificationPreferenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API for managing notification preferences.
 *
 * <p>Admin users can view/update admin defaults (org-wide).
 * Authenticated users can view/update their own preferences.
 *
 * <p>Per D-12: Admin sets defaults, users override their own.
 * Per D-07: Dashboard is always-on and not controlled here.
 *
 * <p>User identity is always extracted from the JWT subject (never from the
 * request body) to prevent privilege escalation (T-09-08).
 */
@RestController
@RequestMapping("/api/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    /**
     * Get the current user's notification preferences (merged with admin defaults).
     */
    @GetMapping
    public ResponseEntity<List<NotificationPreference>> getMyPreferences(
            JwtAuthenticationToken jwt) {
        UUID userId = UUID.fromString(jwt.getToken().getSubject());
        List<NotificationPreference> prefs = preferenceService.getUserPreferences(userId);
        if (prefs.isEmpty()) {
            // Fall back to admin defaults for display
            prefs = preferenceService.getAdminDefaults();
        }
        return ResponseEntity.ok(prefs);
    }

    /**
     * Update/create a user's preference for a specific channel.
     */
    @PutMapping
    public ResponseEntity<NotificationPreference> updateMyPreference(
            @RequestBody NotificationPreference preference,
            JwtAuthenticationToken jwt) {
        UUID userId = UUID.fromString(jwt.getToken().getSubject());
        // Users can only update their own preferences (not admin defaults)
        preference.setUserId(userId);
        preference.setAdminDefault(false);
        NotificationPreference saved = preferenceService.save(preference);
        return ResponseEntity.ok(saved);
    }

    /**
     * Admin-only: get admin default preferences.
     */
    @GetMapping("/defaults")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<NotificationPreference>> getAdminDefaults() {
        return ResponseEntity.ok(preferenceService.getAdminDefaults());
    }

    /**
     * Admin-only: update an admin default preference.
     */
    @PutMapping("/defaults")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationPreference> updateAdminDefault(
            @RequestBody NotificationPreference preference) {
        preference.setUserId(null);
        preference.setAdminDefault(true);
        NotificationPreference saved = preferenceService.save(preference);
        return ResponseEntity.ok(saved);
    }
}
