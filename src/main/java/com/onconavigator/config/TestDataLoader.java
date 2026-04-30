package com.onconavigator.config;

import com.onconavigator.domain.enums.CancerType;
import com.onconavigator.service.PathwayService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Inserts a test patient and starts a pathway workflow for Phase 2 manual testing.
 * Only runs with the "local" profile. Remove after testing.
 */
@Component
@Profile("local")
@Order(1)
public class TestDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TestDataLoader.class);
    private static final UUID SYSTEM_USER = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final EntityManager entityManager;
    private final SecretKey secretKey;
    private final PathwayService pathwayService;
    private final TransactionTemplate txTemplate;

    public TestDataLoader(EntityManager entityManager,
                          @Value("${onconavigator.encryption.key}") String encryptionKey,
                          PathwayService pathwayService,
                          TransactionTemplate txTemplate) {
        this.entityManager = entityManager;
        this.secretKey = new SecretKeySpec(Base64.getDecoder().decode(encryptionKey), "AES");
        this.pathwayService = pathwayService;
        this.txTemplate = txTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Step 1: Ensure test patient exists (committed transaction)
        UUID patientId = txTemplate.execute(status -> ensureTestPatient());

        // Step 2: Start workflow and signal AFTER the patient is committed
        if (patientId != null) {
            startWorkflowAndSignal(patientId);
        }
    }

    private UUID ensureTestPatient() {
        @SuppressWarnings("unchecked")
        List<Object> existing = entityManager.createNativeQuery(
                "SELECT id FROM patients WHERE created_by = ?1")
                .setParameter(1, SYSTEM_USER)
                .getResultList();
        if (!existing.isEmpty()) {
            UUID existingId = (UUID) existing.getFirst();
            log.info("Test patient already exists: id={}", existingId);
            return existingId;
        }

        UUID patientId = UUID.randomUUID();
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);

        entityManager.createNativeQuery(
            "INSERT INTO patients (id, first_name_encrypted, last_name_encrypted, " +
            "date_of_birth_encrypted, mrn_encrypted, cancer_type, cancer_stage, " +
            "diagnosis_date, status, created_at, updated_at, created_by) " +
            "VALUES (:id, :fn, :ln, :dob, :mrn, 'BREAST'::cancer_type, 'II', " +
            ":diagDate, 'ACTIVE'::patient_status, NOW(), NOW(), :createdBy)")
            .setParameter("id", patientId)
            .setParameter("fn", encrypt("Test"))
            .setParameter("ln", encrypt("Patient"))
            .setParameter("dob", encrypt("1970-01-01"))
            .setParameter("mrn", encrypt("TEST-001"))
            .setParameter("diagDate", diagnosisDate)
            .setParameter("createdBy", SYSTEM_USER)
            .executeUpdate();

        log.info("TEST PATIENT CREATED: id={}, diagnosed={} (35 days ago)", patientId, diagnosisDate);
        return patientId;
    }

    private void startWorkflowAndSignal(UUID patientId) {
        log.info("========================================");
        try {
            String runId = pathwayService.startPathwayMonitoring(patientId, CancerType.BREAST);
            log.info("PATHWAY WORKFLOW STARTED: runId={}", runId);
        } catch (Exception e) {
            log.warn("Workflow start skipped (may already exist): {}", e.getMessage());
        }

        try {
            Thread.sleep(3000); // Give workflow time to start and enter await
            pathwayService.signalCareEventChanged(patientId, UUID.randomUUID());
            log.info("SIGNAL SENT: workflow will evaluate now");
        } catch (Exception e) {
            log.warn("Signal failed: {}", e.getMessage());
        }

        log.info("");
        log.info("Check alerts in ~5 seconds:");
        log.info("  docker exec -it onco-postgres psql -U onco_app -d onconavigator -c \"SELECT id, alert_type, pathway_step_name, status FROM alerts WHERE patient_id = '{}';\"", patientId);
        log.info("========================================");
    }

    private byte[] encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }
}
