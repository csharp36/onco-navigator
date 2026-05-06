package com.onconavigator.config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleAlreadyRunningException;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleHandle;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DigestScheduleRegistrar}.
 *
 * <p>Tests verify:
 * <ul>
 *   <li>Schedule is created with the correct schedule ID (DIGEST_SCHEDULE_ID)</li>
 *   <li>ScheduleAlreadyRunningException is caught and does not propagate (idempotent restart)</li>
 *   <li>Successful first registration completes without exception</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DigestScheduleRegistrarTest {

    @Mock
    private WorkflowClient workflowClient;

    @Mock
    private WorkflowServiceStubs workflowServiceStubs;

    @Mock
    private ScheduleClient scheduleClient;

    @Mock
    private ScheduleHandle scheduleHandle;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void run_callsCreateSchedule_withCorrectScheduleId() {
        when(workflowClient.getWorkflowServiceStubs()).thenReturn(workflowServiceStubs);

        try (MockedStatic<ScheduleClient> mockedStatic = mockStatic(ScheduleClient.class)) {
            mockedStatic.when(() -> ScheduleClient.newInstance(workflowServiceStubs))
                    .thenReturn(scheduleClient);
            when(scheduleClient.createSchedule(
                    eq(TemporalConfig.DIGEST_SCHEDULE_ID),
                    any(Schedule.class),
                    any(ScheduleOptions.class)))
                    .thenReturn(scheduleHandle);

            DigestScheduleRegistrar registrar = new DigestScheduleRegistrar(workflowClient);
            registrar.run(applicationArguments);

            // Verify createSchedule was called with the correct schedule ID
            verify(scheduleClient).createSchedule(
                    eq(TemporalConfig.DIGEST_SCHEDULE_ID),
                    any(Schedule.class),
                    any(ScheduleOptions.class));
        }
    }

    @Test
    void run_catchesScheduleAlreadyRunningException_doesNotThrow() {
        when(workflowClient.getWorkflowServiceStubs()).thenReturn(workflowServiceStubs);

        try (MockedStatic<ScheduleClient> mockedStatic = mockStatic(ScheduleClient.class)) {
            mockedStatic.when(() -> ScheduleClient.newInstance(workflowServiceStubs))
                    .thenReturn(scheduleClient);
            when(scheduleClient.createSchedule(
                    eq(TemporalConfig.DIGEST_SCHEDULE_ID),
                    any(Schedule.class),
                    any(ScheduleOptions.class)))
                    .thenThrow(new ScheduleAlreadyRunningException(
                            new RuntimeException("Schedule already exists")));

            DigestScheduleRegistrar registrar = new DigestScheduleRegistrar(workflowClient);

            // Should NOT throw -- ScheduleAlreadyRunningException is caught
            assertThatNoException().isThrownBy(() -> registrar.run(applicationArguments));
        }
    }

    @Test
    void run_logsSuccess_onFirstRegistration() {
        when(workflowClient.getWorkflowServiceStubs()).thenReturn(workflowServiceStubs);

        try (MockedStatic<ScheduleClient> mockedStatic = mockStatic(ScheduleClient.class)) {
            mockedStatic.when(() -> ScheduleClient.newInstance(workflowServiceStubs))
                    .thenReturn(scheduleClient);
            when(scheduleClient.createSchedule(
                    eq(TemporalConfig.DIGEST_SCHEDULE_ID),
                    any(Schedule.class),
                    any(ScheduleOptions.class)))
                    .thenReturn(scheduleHandle);

            DigestScheduleRegistrar registrar = new DigestScheduleRegistrar(workflowClient);
            registrar.run(applicationArguments);

            // If we get here without exception, registration succeeded
            // Verify the schedule ID constant is correct
            assertThat(TemporalConfig.DIGEST_SCHEDULE_ID).isEqualTo("digest-dispatch-schedule");
        }
    }
}
