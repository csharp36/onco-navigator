package com.onconavigator.notification;

import com.onconavigator.domain.Alert;
import com.onconavigator.domain.NotificationLog;
import com.onconavigator.domain.NotificationPendingQueue;
import com.onconavigator.domain.NotificationPreference;
import com.onconavigator.domain.enums.AlertStatus;
import com.onconavigator.domain.enums.AlertType;
import com.onconavigator.domain.enums.NotificationChannel;
import com.onconavigator.repository.NotificationLogRepository;
import com.onconavigator.repository.NotificationPendingQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LoggingNotificationService}.
 *
 * <p>Tests cover the full notification routing pipeline:
 * <ul>
 *   <li>No dispatch when no enabled preferences exist</li>
 *   <li>Severity-filtered alerts skip dispatch</li>
 *   <li>Immediate dispatch logs and saves NotificationLog</li>
 *   <li>Quiet-hours active queues to notification_pending_queue</li>
 *   <li>Digest-enabled queues to notification_pending_queue</li>
 *   <li>Admin default rows with null userId are skipped</li>
 *   <li>dispatchFromQueue persists NotificationLog</li>
 * </ul>
 *
 * <p>PHI safety: Test data uses synthetic "Test Patient" / "MRN001" values. No real PHI.
 */
@ExtendWith(MockitoExtension.class)
class LoggingNotificationServiceTest {

    @Mock
    private NotificationPreferenceService preferenceService;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private NotificationPendingQueueRepository pendingQueueRepository;

    private LoggingNotificationService service;

    private static final UUID ALERT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PATIENT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID USER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @BeforeEach
    void setUp() {
        service = new LoggingNotificationService(preferenceService,
                notificationLogRepository, pendingQueueRepository);
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:5173");
    }

    private Alert createTestAlert() {
        Alert alert = new Alert();
        alert.setId(ALERT_ID);
        alert.setPatientId(PATIENT_ID);
        alert.setAlertType(AlertType.MISSING_EVENT);
        alert.setPathwayStepName("Surgery");
        alert.setDeviationDescription("Surgery not completed within window.");
        alert.setSuggestedAction("Contact surgeon.");
        alert.setMissingSummary("Surgery not completed.");
        alert.setStatus(AlertStatus.OPEN);
        return alert;
    }

    private NotificationPreference createEnabledPreference(UUID userId,
                                                            NotificationChannel channel) {
        NotificationPreference pref = new NotificationPreference();
        pref.setId(UUID.randomUUID());
        pref.setUserId(userId);
        pref.setChannel(channel);
        pref.setEnabled(true);
        pref.setAlertTypeFilter(new String[0]);
        pref.setTimezone("UTC");
        pref.setDigestEnabled(false);
        return pref;
    }

    // ---- Tests ----

    @Test
    void dispatchForAlert_noEnabledPreferences_noDispatch() {
        Alert alert = createTestAlert();

        // No enabled preferences for any channel
        for (NotificationChannel channel : NotificationChannel.values()) {
            when(preferenceService.getAllEnabledPreferences(channel)).thenReturn(List.of());
        }

        service.dispatchForAlert(alert, "Test Patient", "MRN001");

        verify(notificationLogRepository, never()).save(any());
        verify(pendingQueueRepository, never()).save(any());
    }

    @Test
    void dispatchForAlert_enabledPreference_severityFiltered_noDispatch() {
        Alert alert = createTestAlert();

        NotificationPreference pref = createEnabledPreference(USER_ID, NotificationChannel.TEAMS);
        pref.setAlertTypeFilter(new String[]{"DELAYED_EVENT"}); // not MISSING_EVENT

        when(preferenceService.getAllEnabledPreferences(NotificationChannel.TEAMS))
                .thenReturn(List.of(pref));
        when(preferenceService.getAllEnabledPreferences(NotificationChannel.EMAIL))
                .thenReturn(List.of());
        when(preferenceService.passesSeverityFilter(pref, "MISSING_EVENT")).thenReturn(false);

        service.dispatchForAlert(alert, "Test Patient", "MRN001");

        verify(notificationLogRepository, never()).save(any());
        verify(pendingQueueRepository, never()).save(any());
    }

    @Test
    void dispatchForAlert_enabledPreference_immediateDispatch_logsAndSaves() {
        Alert alert = createTestAlert();

        NotificationPreference pref = createEnabledPreference(USER_ID, NotificationChannel.TEAMS);

        when(preferenceService.getAllEnabledPreferences(NotificationChannel.TEAMS))
                .thenReturn(List.of(pref));
        when(preferenceService.getAllEnabledPreferences(NotificationChannel.EMAIL))
                .thenReturn(List.of());
        when(preferenceService.passesSeverityFilter(pref, "MISSING_EVENT")).thenReturn(true);
        when(preferenceService.isInQuietHours(pref)).thenReturn(false);

        service.dispatchForAlert(alert, "Test Patient", "MRN001");

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());

        NotificationLog saved = logCaptor.getValue();
        assertThat(saved.getAlertId()).isEqualTo(ALERT_ID);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.TEAMS);
        assertThat(saved.isDigest()).isFalse();
        assertThat(saved.getStatus()).isEqualTo("SENT");
        assertThat(saved.getRenderedContent()).contains("Test Patient");
    }

    @Test
    void dispatchForAlert_enabledPreference_quietHoursActive_queuesNotification() {
        Alert alert = createTestAlert();

        NotificationPreference pref = createEnabledPreference(USER_ID, NotificationChannel.TEAMS);

        when(preferenceService.getAllEnabledPreferences(NotificationChannel.TEAMS))
                .thenReturn(List.of(pref));
        when(preferenceService.getAllEnabledPreferences(NotificationChannel.EMAIL))
                .thenReturn(List.of());
        when(preferenceService.passesSeverityFilter(pref, "MISSING_EVENT")).thenReturn(true);
        when(preferenceService.isInQuietHours(pref)).thenReturn(true);
        when(preferenceService.computeQuietHoursEnd(pref))
                .thenReturn(OffsetDateTime.now().plusHours(6));

        service.dispatchForAlert(alert, "Test Patient", "MRN001");

        verify(notificationLogRepository, never()).save(any());

        ArgumentCaptor<NotificationPendingQueue> queueCaptor =
                ArgumentCaptor.forClass(NotificationPendingQueue.class);
        verify(pendingQueueRepository).save(queueCaptor.capture());

        NotificationPendingQueue queued = queueCaptor.getValue();
        assertThat(queued.getAlertId()).isEqualTo(ALERT_ID);
        assertThat(queued.getUserId()).isEqualTo(USER_ID);
        assertThat(queued.getChannel()).isEqualTo(NotificationChannel.TEAMS);
        assertThat(queued.getHoldType()).isEqualTo("QUIET_HOURS");
        assertThat(queued.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void dispatchForAlert_enabledPreference_digestMode_queuesNotification() {
        Alert alert = createTestAlert();

        NotificationPreference pref = createEnabledPreference(USER_ID, NotificationChannel.TEAMS);
        pref.setDigestEnabled(true);
        pref.setDigestIntervalHours(4);

        when(preferenceService.getAllEnabledPreferences(NotificationChannel.TEAMS))
                .thenReturn(List.of(pref));
        when(preferenceService.getAllEnabledPreferences(NotificationChannel.EMAIL))
                .thenReturn(List.of());
        when(preferenceService.passesSeverityFilter(pref, "MISSING_EVENT")).thenReturn(true);

        service.dispatchForAlert(alert, "Test Patient", "MRN001");

        verify(notificationLogRepository, never()).save(any());

        ArgumentCaptor<NotificationPendingQueue> queueCaptor =
                ArgumentCaptor.forClass(NotificationPendingQueue.class);
        verify(pendingQueueRepository).save(queueCaptor.capture());

        NotificationPendingQueue queued = queueCaptor.getValue();
        assertThat(queued.getAlertId()).isEqualTo(ALERT_ID);
        assertThat(queued.getUserId()).isEqualTo(USER_ID);
        assertThat(queued.getHoldType()).isEqualTo("DIGEST");
        assertThat(queued.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void dispatchForAlert_adminDefaultWithNullUserId_skipped() {
        Alert alert = createTestAlert();

        // Admin default row has userId = null
        NotificationPreference adminPref = createEnabledPreference(null, NotificationChannel.TEAMS);
        adminPref.setAdminDefault(true);

        when(preferenceService.getAllEnabledPreferences(NotificationChannel.TEAMS))
                .thenReturn(List.of(adminPref));
        when(preferenceService.getAllEnabledPreferences(NotificationChannel.EMAIL))
                .thenReturn(List.of());

        service.dispatchForAlert(alert, "Test Patient", "MRN001");

        // Admin default rows with null userId should be skipped (no one to notify)
        verify(notificationLogRepository, never()).save(any());
        verify(pendingQueueRepository, never()).save(any());
    }

    @Test
    void dispatchFromQueue_savesNotificationLog() {
        UUID alertId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String renderedContent = "Rendered test content";

        service.dispatchFromQueue(alertId, userId, NotificationChannel.EMAIL, renderedContent, true);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());

        NotificationLog saved = logCaptor.getValue();
        assertThat(saved.getAlertId()).isEqualTo(alertId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(saved.getRenderedContent()).isEqualTo("Rendered test content");
        assertThat(saved.isDigest()).isTrue();
        assertThat(saved.getStatus()).isEqualTo("SENT");
    }
}
