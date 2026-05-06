package com.onconavigator.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NotificationPayload#render()}.
 *
 * <p>Tests verify the human-readable notification format includes all required fields:
 * severity, patient name, MRN, step, missingSummary, suggestedAction, and deepLink.
 *
 * <p>PHI safety: Test data uses synthetic "Test Patient" / "MRN001" values. No real PHI.
 */
class NotificationPayloadTest {

    @Test
    void render_producesExpectedFormat_withAllFields() {
        NotificationPayload payload = new NotificationPayload(
                "Jane Doe",
                "MRN-12345",
                "CT Scan",
                "OVERDUE",
                "CT scan not completed within 14 days of referral.",
                "Contact radiology to confirm scheduling.",
                "http://localhost:5173/patients/abc-123"
        );

        String rendered = payload.render();

        // Verify severity label is present in brackets
        assertThat(rendered).contains("[OVERDUE]");
        // Verify patient name is rendered
        assertThat(rendered).contains("Jane Doe");
        // Verify MRN format
        assertThat(rendered).contains("(MRN: MRN-12345)");
        // Verify step name
        assertThat(rendered).contains("CT Scan");
        // Verify missingSummary section
        assertThat(rendered).contains("What is missing: CT scan not completed within 14 days of referral.");
        // Verify suggestedAction section
        assertThat(rendered).contains("Suggested action: Contact radiology to confirm scheduling.");
        // Verify deepLink
        assertThat(rendered).contains("View: http://localhost:5173/patients/abc-123");
    }

    @Test
    void render_handlesNullMissingSummary_gracefully() {
        NotificationPayload payload = new NotificationPayload(
                "John Smith",
                "MRN-99999",
                "Surgery",
                "MISSING",
                null,  // missingSummary is null
                "Review patient pathway and take corrective action.",
                "http://localhost:5173/patients/xyz-789"
        );

        String rendered = payload.render();

        // Null missingSummary should render as "(none)"
        assertThat(rendered).contains("What is missing: (none)");
        // Other fields should still be present
        assertThat(rendered).contains("[MISSING]");
        assertThat(rendered).contains("John Smith");
        assertThat(rendered).contains("(MRN: MRN-99999)");
    }

    @Test
    void render_handlesNullSuggestedAction_gracefully() {
        NotificationPayload payload = new NotificationPayload(
                "Alice Brown",
                "MRN-55555",
                "Chemotherapy",
                "DELAYED",
                "Chemotherapy not started within expected window.",
                null,  // suggestedAction is null
                "http://localhost:5173/patients/def-456"
        );

        String rendered = payload.render();

        // Null suggestedAction should render as "(none)"
        assertThat(rendered).contains("Suggested action: (none)");
        // missingSummary should still render normally
        assertThat(rendered).contains("What is missing: Chemotherapy not started within expected window.");
    }

    @Test
    void render_handlesAllNullableFieldsNull() {
        NotificationPayload payload = new NotificationPayload(
                "Patient Zero",
                "MRN-00000",
                "Follow Up",
                "UNCONFIRMED",
                null,
                null,
                "http://localhost:5173/patients/ghi-000"
        );

        String rendered = payload.render();

        assertThat(rendered).contains("What is missing: (none)");
        assertThat(rendered).contains("Suggested action: (none)");
        assertThat(rendered).contains("[UNCONFIRMED]");
        assertThat(rendered).contains("Patient Zero");
    }
}
