package com.onconavigator.web;

import com.onconavigator.service.AlertService;
import com.onconavigator.service.PatientService;
import com.onconavigator.web.dto.AlertResponse;
import com.onconavigator.web.dto.DashboardStatsResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the nurse navigator dashboard summary statistics.
 *
 * <p>Returns aggregated metrics for the dashboard header: total open alerts,
 * active patients, on-track patients (active minus those with open alerts),
 * and the top 5 urgent alerts for the immediate action queue.
 *
 * <p>Injects {@link PatientService} (not PatientRepository directly) — controllers
 * must route through the service layer for consistent business logic encapsulation.
 *
 * <p>Accessible to all authenticated users — the dashboard is the landing page
 * for all clinical roles.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final AlertService alertService;
    private final PatientService patientService;

    public DashboardController(AlertService alertService, PatientService patientService) {
        this.alertService = alertService;
        this.patientService = patientService;
    }

    /**
     * Returns aggregated dashboard statistics for the nurse navigator landing page.
     *
     * <p>Statistics:
     * <ul>
     *   <li>{@code openAlertCount} — total number of OPEN alerts across all patients</li>
     *   <li>{@code activePatients} — count of patients with ACTIVE status</li>
     *   <li>{@code onTrackPatients} — active patients minus those with at least one open alert
     *       (clamped to 0 to avoid negative values)</li>
     *   <li>{@code topUrgentAlerts} — first 5 alerts from the severity-ordered queue
     *       (OVERDUE → MISSING → OUT OF ORDER)</li>
     * </ul>
     *
     * @return dashboard stats response with alert count, patient counts, and top urgent alerts
     */
    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public DashboardStatsResponse getStats() {
        long openAlertCount = alertService.countOpenAlerts();
        long activePatients = patientService.countActivePatients();

        // Compute on-track patients: active patients minus those with at least one open alert
        List<AlertResponse> allAlerts = alertService.getOpenAlerts();
        long patientsWithAlerts = allAlerts.stream()
                .map(AlertResponse::patientId)
                .distinct()
                .count();
        long onTrackPatients = Math.max(0, activePatients - patientsWithAlerts);

        // Top 5 urgent alerts — already severity-ordered by AlertService
        List<AlertResponse> topUrgent = allAlerts.stream().limit(5).toList();

        return new DashboardStatsResponse(openAlertCount, activePatients, onTrackPatients, topUrgent);
    }
}
