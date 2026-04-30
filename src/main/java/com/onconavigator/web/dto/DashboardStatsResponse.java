package com.onconavigator.web.dto;

import java.util.List;

/**
 * Response DTO for the nurse navigator dashboard summary statistics (per D-10).
 *
 * <p>Provides the key metrics needed for the dashboard header:
 * open alert count, total active patients, and how many are on track.
 * Also includes the top urgent alerts for the immediate action queue.
 *
 * @param topUrgentAlerts top N alerts by severity ordering (DELAYED > MISSING > OUT_OF_ORDER),
 *                        limited by the service to a manageable display count (typically 5-10)
 */
public record DashboardStatsResponse(
        long openAlertCount,
        long activePatients,
        long onTrackPatients,
        List<AlertResponse> topUrgentAlerts
) {}
