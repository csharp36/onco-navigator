package com.onconavigator.notification;

import com.onconavigator.domain.NotificationPreference;
import com.onconavigator.domain.enums.NotificationChannel;
import com.onconavigator.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Loads and merges notification preferences with admin-default-to-user-override semantics.
 *
 * <p>Per D-12: Admin sets organization-wide defaults (user_id = NULL, is_admin_default = TRUE).
 * Individual users can override their own channel preferences. User overrides always win.
 *
 * <p>Also provides severity filtering (D-11), quiet-hours detection (D-11), and
 * quiet-hours end computation for the pending queue hold_until value.
 */
@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    /**
     * Loads effective preference for a user on a channel.
     * User override wins; falls back to admin default.
     */
    public Optional<NotificationPreference> getEffectivePreference(UUID userId,
                                                                    NotificationChannel channel) {
        Optional<NotificationPreference> userPref =
            preferenceRepository.findByUserIdAndChannel(userId, channel);
        if (userPref.isPresent()) return userPref;

        return preferenceRepository.findByUserIdIsNullAndAdminDefaultTrue()
            .stream()
            .filter(p -> p.getChannel() == channel)
            .findFirst();
    }

    /**
     * Checks if the alert type passes the severity filter for this preference.
     * Empty alertTypeFilter array means "receive all types".
     */
    public boolean passesSeverityFilter(NotificationPreference pref, String alertTypeName) {
        String[] filter = pref.getAlertTypeFilter();
        if (filter == null || filter.length == 0) return true;
        return Arrays.asList(filter).contains(alertTypeName);
    }

    /**
     * Checks if the current time is within the user's quiet hours.
     * Uses the preference's timezone field.
     */
    public boolean isInQuietHours(NotificationPreference pref) {
        if (pref.getQuietHoursStart() == null || pref.getQuietHoursEnd() == null) return false;

        ZoneId zone = ZoneId.of(pref.getTimezone() != null ? pref.getTimezone() : "UTC");
        int currentHour = LocalTime.now(zone).getHour();
        int start = pref.getQuietHoursStart();
        int end = pref.getQuietHoursEnd();

        if (start < end) {
            // e.g., start=9, end=17 means 9-16 are quiet
            return currentHour >= start && currentHour < end;
        } else {
            // Wraps midnight: start=22, end=7 means 22-23 and 0-6 are quiet
            return currentHour >= start || currentHour < end;
        }
    }

    /**
     * Computes hold_until for quiet hours (next end hour).
     */
    public OffsetDateTime computeQuietHoursEnd(NotificationPreference pref) {
        ZoneId zone = ZoneId.of(pref.getTimezone() != null ? pref.getTimezone() : "UTC");
        LocalTime endTime = LocalTime.of(pref.getQuietHoursEnd(), 0);
        OffsetDateTime now = OffsetDateTime.now(zone);
        OffsetDateTime candidate = now.with(endTime);
        if (candidate.isBefore(now) || candidate.isEqual(now)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    /**
     * Returns all enabled preferences across all users for notification dispatch.
     */
    public List<NotificationPreference> getAllEnabledPreferences(NotificationChannel channel) {
        return preferenceRepository.findByChannelAndEnabledTrue(channel);
    }

    /**
     * Returns preferences for a specific user (for the preference controller).
     */
    public List<NotificationPreference> getUserPreferences(UUID userId) {
        return preferenceRepository.findByUserId(userId);
    }

    /**
     * Returns admin default preferences.
     */
    public List<NotificationPreference> getAdminDefaults() {
        return preferenceRepository.findByUserIdIsNullAndAdminDefaultTrue();
    }

    /**
     * Save or update a preference.
     */
    public NotificationPreference save(NotificationPreference preference) {
        return preferenceRepository.save(preference);
    }
}
