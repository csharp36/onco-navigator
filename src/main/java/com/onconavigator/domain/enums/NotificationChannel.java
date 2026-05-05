package com.onconavigator.domain.enums;

/**
 * Notification delivery channels for external alert dispatch.
 * Maps to the notification_channel PostgreSQL enum.
 *
 * <p>Dashboard is always-on per D-07 and is not a configurable channel.
 * notification_preferences controls TEAMS and EMAIL only.
 */
public enum NotificationChannel {
    TEAMS,
    EMAIL
}
