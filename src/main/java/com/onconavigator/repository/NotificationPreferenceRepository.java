package com.onconavigator.repository;

import com.onconavigator.domain.NotificationPreference;
import com.onconavigator.domain.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link NotificationPreference} entities.
 *
 * <p>Supports loading admin defaults (user_id IS NULL, is_admin_default = TRUE)
 * and user-specific overrides. Per D-12, admin defaults apply to all users unless
 * overridden by a user-specific preference row.
 */
@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    /**
     * Load user-specific preference for a given channel.
     *
     * @param userId  the user UUID (from JWT subject)
     * @param channel the notification channel
     * @return the user's preference for that channel, if set
     */
    Optional<NotificationPreference> findByUserIdAndChannel(UUID userId, NotificationChannel channel);

    /**
     * Load all preferences for a user (all channels).
     *
     * @param userId the user UUID
     * @return all channel preferences for the user
     */
    List<NotificationPreference> findByUserId(UUID userId);

    /**
     * Load admin default rows (user_id IS NULL and is_admin_default = TRUE).
     * These serve as the org-wide default preferences before user overrides.
     *
     * @return admin default preference rows
     */
    List<NotificationPreference> findByUserIdIsNullAndAdminDefaultTrue();

    /**
     * Load all enabled preferences for a specific channel.
     * Used to find all users who should receive notifications on a channel.
     *
     * @param channel the notification channel
     * @return enabled preferences for the channel
     */
    List<NotificationPreference> findByChannelAndEnabledTrue(NotificationChannel channel);
}
