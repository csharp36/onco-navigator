---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Completed 04-04-PLAN.md
last_updated: "2026-05-01T21:26:48Z"
last_activity: 2026-05-01 -- Phase 04 Plan 04 completed
progress:
  total_phases: 4
  completed_phases: 3
  total_plans: 22
  completed_plans: 19
  percent: 86
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-29)

**Core value:** Prevent patients from falling through the cracks by systematically watching every patient's care pathway and surfacing deviations before they become wasted visits, delayed treatments, or invisible gaps.
**Current focus:** Phase 04 — ai-document-ingestion

## Current Position

Phase: 04 (ai-document-ingestion) — EXECUTING
Plan: 5 of 7
Status: Plan 04 complete, ready for Plan 05
Last activity: 2026-05-01 -- Phase 04 Plan 04 completed

Progress: [████████░░] 86%

## Performance Metrics

**Velocity:**

- Total plans completed: 3
- Average duration: 10 minutes
- Total execution time: 0.50 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-hipaa-foundation | 4/5 | ~70 min | ~17 min |

**Recent Trend:**

- Last 5 plans: 01-01 (5 min), 01-02 (12 min), 01-03 (4 min), 01-05 (40 min)
- Trend: stable (01-05 was longer due to Docker Desktop compatibility debugging)

*Updated after each plan completion*
| Phase 02-pathway-engine P01 | 15min | 2 tasks | 7 files |
| Phase 02-pathway-engine P02 | 6min | 2 tasks | 11 files |
| Phase 02-pathway-engine P03 | 10min | 2 tasks | 4 files |
| Phase 02-pathway-engine P04 | 9min | 2 tasks | 5 files |
| Phase 04-ai-document-ingestion P01 | 5min | 2 tasks | 12 files |
| Phase 04-ai-document-ingestion P02 | 9min | 2 tasks | 18 files |
| Phase 04-ai-document-ingestion P03 | 7min | 2 tasks | 7 files |
| Phase 04-ai-document-ingestion P04 | 4min | 2 tasks | 8 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Roadmap]: Coarse granularity applied — merged research's 6 phases into 4. Temporal skeleton and pathway engine merged into Phase 2; REST API and dashboard merged into Phase 3; AI integration and AWS hardening merged into Phase 4.
- [Roadmap]: INFR-02 (Spring profiles) assigned to Phase 1 — profile infrastructure is foundation work, AWS-specific config validation occurs naturally in Phase 4 execution.
- [01-01]: Jasypt ENC(placeholder_encrypted_password) in application-local.yml — real password encrypted at developer setup time; prevents plaintext credentials in committed config.
- [01-01]: docker-compose.yml app service commented out — during active dev, run Spring Boot directly via mvnw spring-boot:run to avoid Docker rebuild on each change.
- [01-01]: KC_DB: dev-mem for Keycloak in local dev — avoids needing separate Keycloak PostgreSQL schema setup.
- [01-02]: CareEventRepository uses findByPatient_IdOrderByEventDateDesc — CareEvent maps patient as @ManyToOne, Spring Data requires underscore traversal for relationship property paths.
- [01-02]: AuditLogEntry uses IDENTITY (BIGSERIAL) not UUID — audit_log uses BIGSERIAL primary key for sequential ordering and index efficiency.
- [01-02]: PatientRepository.findByMrn is a documented stub — AES-GCM random IV prevents ciphertext equality; Phase 3 will add HMAC index token for MRN search.
- [01-02]: EncryptionConverter uses ApplicationContextProvider (static context accessor) — JPA converters are instantiated by Hibernate outside Spring lifecycle, constructor injection unavailable.
- [01-03]: @Order(HIGHEST_PRECEDENCE + 10) on AuditLoggingFilter — runs early, calls filterChain.doFilter() first so security context is populated when audit data is extracted.
- [01-03]: REQUIRES_NEW transaction on AuditService.logAccess — audit entry commits independently; rolled-back business operations still generate audit records.
- [01-03]: Nil UUID for anonymous actors — satisfies NOT NULL constraint on audit_log.actor_id without schema change.
- [01-03]: @MockitoBean replaces deprecated @MockBean — Spring Boot 3.4+ replacement used in test classes.
- [01-04]: Tailwind v4 — no tailwind.config.ts; all config in src/app.css via @theme inline CSS block; @tailwindcss/vite plugin handles compilation.
- [01-04]: TanStack Router routeTree.gen.ts pre-generated via vite build before first tsc run — avoids Cannot find module on first build.
- [01-04]: TypeScript 6 erasableSyntaxOnly default disallows class parameter properties; ApiError refactored to explicit field assignment.
- [01-04]: ignoreDeprecations: 6.0 added to tsconfig.app.json — TypeScript 6 deprecates baseUrl but paths alias still requires it.
- [01-05]: AuditLoggingFilter moved inside Spring Security chain via addFilterAfter(BearerTokenAuthenticationFilter) — running before FilterChainProxy caused SecurityContextHolder to be empty when audit data was extracted post-doFilter().
- [01-05]: Testcontainers BOM 1.21.3 required for Docker Desktop 4.59 / Apple Silicon — docker-java API version 1.32 (BOM 1.20.4) rejected by Docker Desktop 4.59 minimum 1.44.
- [01-05]: HealthCheckController uses static UP response — avoids HealthEndpoint unavailability in @WebMvcTest slices; sufficient for Docker HEALTHCHECK liveness check.
- [Phase 02-01]: AnchorType placed in domain.dto (not domain.enums) — DTO-level enum for JSONB deserialization only, not a PostgreSQL column type
- [Phase 02-01]: PathwayStep uses Java record canonical constructor — Jackson maps JSONB field names directly without @JsonProperty because V6 seed camelCase keys match record component names exactly
- [Phase 02-01]: PhysicianOverride all fields updatable=false — overrides are write-once by clinical design; UNIQUE index on (patient_id, pathway_step_id) prevents duplicate suppression records
- [Phase 02-02]: alertTypeStr passed as String to AlertGenerationActivity.generateAlert — Temporal serializes activity params; String enum name is more robust to schema evolution; implementation converts with AlertType.valueOf(alertTypeStr)
- [Phase 02-02]: Activity stubs declared as instance fields in workflow impl (not inside monitorPathway method body) — conventional Temporal pattern; method-body creation also works but field declaration is consistent with replay determinism
- [Phase 02-02]: PathwayService.startPathwayMonitoring uses WorkflowClient.start (async) — sync call would block until workflow completes (weeks), which is never correct
- [Phase 02-02]: WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE for patient pathway workflows — allows re-enrollment after deactivation without manual workflow ID management
- [Phase 02-03]: PathwayEvaluationActivityImpl creates alerts directly (not via AlertGenerationActivity) -- single-pass evaluation and creation, avoids activity-calling-activity complexity
- [Phase 02-03]: SweepActivityImpl injects WorkflowClient directly (not PathwayService) -- needs REJECT_DUPLICATE policy vs PathwayService ALLOW_DUPLICATE; avoids circular dependency
- [Phase 02-fix]: JDBC URL requires ?stringtype=unspecified for PostgreSQL custom enum types (cancer_type, patient_status, etc.) — without it, Hibernate sends varchar parameters that PostgreSQL rejects with "operator does not exist: cancer_type = character varying"
- [Phase 02-fix]: temporal-spring-boot-starter does NOT auto-register @Component activity beans on workers — explicit activity-beans list required in application-local.yml workers config
- [Phase 02-fix]: Code review found and fixed 7 critical issues + 1 warning: alert dedup TOCTOU race (V7 partial unique index), missing @Transactional on evaluate/closeOpenAlerts, OUT_OF_ORDER fall-through double-alerting, PHI in exception messages, missing @WorkflowImpl annotations, sweep ALLOW_DUPLICATE_FAILED_ONLY policy, EncryptionKeyValidator startup guard
- [Phase 02-04]: Temporal workflow tests use concrete stub activity classes (not Mockito proxies) -- Mockito subclass proxies inherit @ActivityMethod on overriding methods; Temporal's POJOActivityImplMetadata rejects them; concrete stubs with AtomicInteger counters are the correct pattern
- [Phase 02-04]: @Configuration(proxyBeanMethods=false) on TemporalConfig -- constants-only class with private constructor; Spring CGLIB cannot proxy without a visible constructor; proxyBeanMethods=false is semantically correct since no @Bean methods exist
- [Phase 04-01]: ChatClient beans use ChatClient.Builder (injected by Spring AI auto-config) with per-bean temperature/maxTokens overrides -- follows Spring AI 1.1.x recommended pattern
- [Phase 04-01]: ClinicalDocument.careEventId is a plain UUID column (not @ManyToOne) -- document may be created before care event linkage; avoids bidirectional relationship complexity
- [Phase 04-01]: extractedText encrypted via EncryptionConverter; content byte[] relies on storage-level encryption since converter operates on String not byte[]
- [Phase 04-01]: onconavigator.ai.document-classification.enabled=false by default -- feature flag gates Claude calls pending Anthropic BAA
- [Phase 04-03]: Per-call Tesseract instances (new Tesseract() per performOcr call) for virtual thread safety -- no shared bean, no pool
- [Phase 04-03]: OCR confidence heuristic (text length >100 chars = 75) instead of Tess4J native API -- getAPI() unreliable across builds
- [Phase 04-03]: ClaudeVisionService uses AnthropicChatModel directly for multimodal vision -- ChatClient does not support Media objects in .user() lambda
- [Phase 04-03]: DocumentProcessingService skips persistence without patient link (patient_id NOT NULL) -- frontend handles patient selection flow
- [Phase 04-03]: Resilience4j fallback methods are public -- CGLIB proxy requirement for @CircuitBreaker annotation processing
- [Phase 04-04]: DocumentUploadController uses hasRole CARE_COORDINATOR or ADMIN on ALL three endpoints (upload, content, patient docs) -- consistent role enforcement, not just isAuthenticated
- [Phase 04-04]: Content streaming endpoint has in-method role verification as BOLA defense-in-depth (T-04-11) -- V2 TODO for patient-level access control
- [Phase 04-04]: CareEvent.documentId is a plain UUID column (not @ManyToOne) -- matches ClinicalDocument.careEventId pattern, avoids bidirectional relationship complexity
- [Phase 04-04]: buildAlert method preserves AI-01 template-first behavior unchanged; Claude called only when step.alertText() is null/blank
- [Phase 04-04]: Generic fallback template in buildAlert includes step name and window days for minimal useful context when circuit breaker is open

### Pending Todos

- Generate a real AES-256 encryption key: `openssl rand -base64 32` and replace placeholder in application-local.yml before running the application.

### Blockers/Concerns

- [Phase 2]: Clinical pathway content for breast/lung/colorectal templates requires oncologist co-authorship (the medical neighbor). Schedule review session before Phase 2 plans execute template authorship.
- [Phase 4]: Anthropic BAA negotiation should start during Phase 1-2 (takes 2-8 weeks). Do not wait until Phase 4 begins.
- [Phase 4]: Temporal self-hosted on ECS Fargate has limited documented examples. May need a deployment spike.

## Deferred Items

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| Search | PatientRepository.findByMrn — AES-GCM random IV prevents DB-level equality search | Deferred to Phase 3 | 01-02 |

## Session Continuity

Last session: 2026-05-01
Stopped at: Completed 04-04-PLAN.md
Resume file: .planning/phases/04-ai-document-ingestion/04-05-PLAN.md
