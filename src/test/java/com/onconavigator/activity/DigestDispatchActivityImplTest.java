package com.onconavigator.activity;

import com.onconavigator.domain.NotificationPendingQueue;
import com.onconavigator.domain.enums.NotificationChannel;
import com.onconavigator.notification.NotificationService;
import com.onconavigator.repository.NotificationPendingQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DigestDispatchActivityImpl}.
 *
 * <p>Tests cover the pending queue drain logic:
 * <ul>
 *   <li>Empty queue: no dispatch calls</li>
 *   <li>QUIET_HOURS items: dispatched individually via NotificationService.dispatchFromQueue</li>
 *   <li>DIGEST items: grouped by user, dispatched via NotificationService.dispatchFromQueue</li>
 *   <li>Mixed types: all items processed</li>
 * </ul>
 *
 * <p>PHI safety: No PHI in test data -- UUIDs are synthetic, rendered content is placeholder text.
 */
@ExtendWith(MockitoExtension.class)
class DigestDispatchActivityImplTest {

    @Mock
    private NotificationPendingQueueRepository pendingQueueRepository;

    @Mock
    private NotificationService notificationService;

    private DigestDispatchActivityImpl activity;

    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        activity = new DigestDispatchActivityImpl(pendingQueueRepository, notificationService);
    }

    private NotificationPendingQueue createPendingItem(UUID userId, String holdType,
                                                        NotificationChannel channel) {
        NotificationPendingQueue item = new NotificationPendingQueue();
        item.setId(UUID.randomUUID());
        item.setAlertId(UUID.randomUUID());
        item.setUserId(userId);
        item.setChannel(channel);
        item.setHoldType(holdType);
        item.setHoldUntil(OffsetDateTime.now().minusMinutes(5));
        item.setRenderedContentEncrypted("Rendered content for " + userId);
        item.setStatus("PENDING");
        return item;
    }

    @Test
    void drainPendingQueue_noReadyItems_doesNothing() {
        when(pendingQueueRepository.findByStatusAndHoldUntilBefore(eq("PENDING"), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        activity.drainPendingQueue();

        verify(notificationService, never()).dispatchFromQueue(any(), any(), any(), any(), anyBoolean());
        verify(pendingQueueRepository, never()).save(any());
    }

    @Test
    void drainPendingQueue_quietHoursItem_dispatchesViaService() {
        NotificationPendingQueue item = createPendingItem(USER_A, "QUIET_HOURS",
                NotificationChannel.TEAMS);

        when(pendingQueueRepository.findByStatusAndHoldUntilBefore(eq("PENDING"), any(OffsetDateTime.class)))
                .thenReturn(List.of(item));

        activity.drainPendingQueue();

        // Verify dispatch via NotificationService (not direct log repository save)
        verify(notificationService).dispatchFromQueue(
                eq(item.getAlertId()),
                eq(USER_A),
                eq(NotificationChannel.TEAMS),
                eq("Rendered content for " + USER_A),
                eq(false) // QUIET_HOURS items are not digest
        );

        // Verify item marked DISPATCHED
        ArgumentCaptor<NotificationPendingQueue> savedCaptor =
                ArgumentCaptor.forClass(NotificationPendingQueue.class);
        verify(pendingQueueRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStatus()).isEqualTo("DISPATCHED");
    }

    @Test
    void drainPendingQueue_digestItems_groupedByUser_dispatchesViaService() {
        // 2 items for user A, 1 item for user B -- all DIGEST type
        NotificationPendingQueue itemA1 = createPendingItem(USER_A, "DIGEST",
                NotificationChannel.TEAMS);
        NotificationPendingQueue itemA2 = createPendingItem(USER_A, "DIGEST",
                NotificationChannel.EMAIL);
        NotificationPendingQueue itemB1 = createPendingItem(USER_B, "DIGEST",
                NotificationChannel.TEAMS);

        when(pendingQueueRepository.findByStatusAndHoldUntilBefore(eq("PENDING"), any(OffsetDateTime.class)))
                .thenReturn(List.of(itemA1, itemA2, itemB1));

        activity.drainPendingQueue();

        // Verify 3 dispatchFromQueue calls (one per item), all with isDigest=true
        verify(notificationService, times(3)).dispatchFromQueue(
                any(UUID.class), any(UUID.class), any(NotificationChannel.class),
                anyString(), eq(true)
        );

        // Verify all 3 items marked DISPATCHED
        verify(pendingQueueRepository, times(3)).save(any(NotificationPendingQueue.class));

        // Capture and verify all items saved as DISPATCHED
        ArgumentCaptor<NotificationPendingQueue> savedCaptor =
                ArgumentCaptor.forClass(NotificationPendingQueue.class);
        verify(pendingQueueRepository, times(3)).save(savedCaptor.capture());
        savedCaptor.getAllValues().forEach(saved ->
                assertThat(saved.getStatus()).isEqualTo("DISPATCHED")
        );
    }

    @Test
    void drainPendingQueue_mixedTypes_allProcessed() {
        // Mix of QUIET_HOURS and DIGEST items
        NotificationPendingQueue quietItem = createPendingItem(USER_A, "QUIET_HOURS",
                NotificationChannel.TEAMS);
        NotificationPendingQueue digestItem1 = createPendingItem(USER_A, "DIGEST",
                NotificationChannel.EMAIL);
        NotificationPendingQueue digestItem2 = createPendingItem(USER_B, "DIGEST",
                NotificationChannel.TEAMS);

        when(pendingQueueRepository.findByStatusAndHoldUntilBefore(eq("PENDING"), any(OffsetDateTime.class)))
                .thenReturn(List.of(quietItem, digestItem1, digestItem2));

        activity.drainPendingQueue();

        // Total dispatchFromQueue calls = 3 (1 quiet + 2 digest)
        verify(notificationService, times(3)).dispatchFromQueue(
                any(UUID.class), any(UUID.class), any(NotificationChannel.class),
                anyString(), anyBoolean()
        );

        // All 3 items saved as DISPATCHED
        verify(pendingQueueRepository, times(3)).save(any(NotificationPendingQueue.class));
    }
}
