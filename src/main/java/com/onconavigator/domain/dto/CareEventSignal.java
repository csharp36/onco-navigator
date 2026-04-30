package com.onconavigator.domain.dto;

import java.util.UUID;

/**
 * Signal payload for care event changes.
 *
 * <p>Carries UUID only — no PHI (SEC-06, T-02-06). The actual care event details
 * are fetched by activity implementations directly from the encrypted database.
 *
 * <p>Note: This record exists for documentation and potential future use (e.g., if the
 * signal payload needs to be extended). The actual signal method on the workflow interface
 * ({@link com.onconavigator.workflow.PatientPathwayWorkflow#careEventChanged}) takes a
 * plain {@code UUID} parameter for simplicity. This record represents the conceptual
 * contract for what may be carried in a care event signal.
 */
public record CareEventSignal(UUID careEventId) {
    // Intentionally minimal: UUID only, no PHI.
}
