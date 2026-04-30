package com.onconavigator.repository;

import com.onconavigator.domain.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link AuditLogEntry} records.
 *
 * <p>This repository is INSERT-only in practice — the application user has no UPDATE or
 * DELETE privileges on the audit_log table (enforced by V3 migration). Calling any
 * JpaRepository mutating methods other than {@code save} for new entities will cause a
 * database permission error, which is the intended behavior.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    /**
     * Find audit records for a specific actor within a time window.
     * Used for HIPAA compliance queries (e.g., "show all accesses by user X in the past 30 days").
     *
     * @param actorId   UUID of the user whose activity to query
     * @param startTime beginning of the time window (inclusive)
     * @param endTime   end of the time window (inclusive)
     * @return audit records in no particular order
     */
    List<AuditLogEntry> findByActorIdAndTimestampBetween(
            UUID actorId, OffsetDateTime startTime, OffsetDateTime endTime);
}
