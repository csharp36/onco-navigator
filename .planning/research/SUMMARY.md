# Project Research Summary

**Project:** Onco-Navigator AI
**Domain:** HIPAA-compliant oncology care pathway monitoring — nurse navigator tooling
**Researched:** 2026-04-29
**Confidence:** HIGH

## Executive Summary

Onco-Navigator is a durable workflow-driven clinical coordination system that monitors oncology patients against predefined care pathway templates and surfaces deviations to nurse navigators. The expert approach centers on three irreducible decisions: (1) durable workflow orchestration via Temporal.io for timer-based deviation detection that survives restarts, (2) HIPAA compliance built into the data model and infrastructure from day one rather than layered on after, and (3) a human-in-the-loop constraint positioning AI as advisory — Claude generates alert text for edge-case deviations, but a nurse decides and acts.

The recommended stack is Java 21 + Spring Boot 3.5 + Temporal Java SDK 1.32 on the backend, with a React 19 + TypeScript dashboard on the frontend, all backed by PostgreSQL 16 and Keycloak 26 for identity. The critical architectural insight is that each patient enrollment gets one long-running Temporal workflow instance. Care events arrive as signals, timers detect missed deadlines, and all side effects (DB writes, Claude API calls, audit writes) are isolated in Activities. This is not incidental to the design — it is the design.

The top risks are: (1) Temporal non-determinism from unversioned code changes to running workflows, (2) HIPAA exposure from incomplete audit trails or PHI leaking into Temporal history, and (3) clinical adoption failure from alert fatigue. The product succeeds or fails on alert quality and data entry friction, not on technology.

## Key Findings

### Recommended Stack

The backend is Java 21 + Spring Boot 3.5 + `temporal-spring-boot-starter:1.32.0`. Spring AI 1.1.0 (GA) wraps the Claude API. Keycloak 26 is the self-hosted OIDC identity provider — the deprecated Keycloak adapter must not be used; correct integration is `spring-boot-starter-oauth2-resource-server`. Flyway 11 requires both `flyway-core` AND `flyway-database-postgresql` JARs. Hibernate Envers provides `@Audited` revision history. The frontend is React 19 + TypeScript 5 + Vite 6 with TanStack Query v5, TanStack Router v1, shadcn/ui + Tailwind v4, Recharts v3.

**Core technologies:**
- **Java 21 LTS**: Runtime — virtual threads (Loom GA), Temporal SDK support, LTS through 2029
- **Spring Boot 3.5**: Framework — BOM manages all Spring library versions; virtual thread config via `spring.threads.virtual.enabled=true`
- **Temporal SDK 1.32 + Server 1.28**: Durable orchestration — timer-based deviation detection that survives restarts
- **PostgreSQL 16**: Primary store — ACID for ePHI; pgcrypto column encryption; used by both application and Temporal persistence (separate schemas/users)
- **Keycloak 26**: OIDC identity provider — self-hosted (no external BAA needed); JWT issuance
- **Spring AI 1.1.0 GA**: Claude API integration — `ChatClient` abstraction
- **Hibernate Envers**: Audit trail — `@Audited` creates `_AUD` revision tables; satisfies HIPAA §164.312(b)
- **React 19 + TanStack Query v5**: Dashboard — server state management, background refresh
- **shadcn/ui + Tailwind v4**: UI components — project-owned, full accessibility control

### Expected Features

**Must have (table stakes — V1):**
- Prioritized patient worklist ranked by alert severity and time-to-next-milestone
- Per-patient pathway status view showing sequence position and overdue items
- Deviation detection engine — missing, delayed, out-of-order events
- Alert queue dashboard — consolidated inbox for all open deviations
- Alert resolution workflow — acknowledge, document, close with timestamp
- Patient record CRUD and care event recording
- Role-based access control (nurse_navigator, care_coordinator, admin)
- Immutable audit trail — every ePHI access logged, 6-year retention
- HIPAA-compliant infrastructure — encryption at rest/transit, no PHI in logs
- Pathway template definitions — breast, lung, colorectal

**Should have (differentiators — V1.x):**
- AI-generated deviation alert descriptions (Claude API) for edge cases
- AI-suggested corrective actions — advisory next step per alert
- Configurable pathway template admin UI
- Pathway progression timeline visualization

**Defer (V2+):**
- SMS/push notifications, EMR/HL7 FHIR integration, predictive at-risk scoring, multi-practice SaaS, reporting suite

### Architecture Approach

Four-layer architecture: React SPA over Spring MVC + WebSocket broker over Temporal Server + Spring Boot workers over PostgreSQL + Claude API. One Temporal workflow instance per patient enrollment, receiving care events as signals, running durable timers for deadline detection, delegating all side effects to Activities.

**Major components:**
1. **PatientPathwayWorkflow** — one durable execution per patient; signal-driven event ingestion; timer-based deviation detection; Continue-As-New at 8K events
2. **Activities** — all side effects: DeviationDetectorActivity, AlertPersistenceActivity, ClaudeAlertGeneratorActivity, AuditLogActivity; each idempotent
3. **Spring MVC + Security Layer** — REST controllers; JWT validation; AuditLoggingFilter runs before controllers
4. **React Dashboard** — reads via REST (TanStack Query); receives push via WebSocket (STOMP/SockJS)
5. **PostgreSQL (app schema)** — patients, care_events, alerts, audit_log, pathway_templates; Envers `_AUD` tables; column-level encryption
6. **Keycloak** — OIDC JWT issuer; role claims validated by Spring Security resource server

### Critical Pitfalls

1. **Temporal workflow non-determinism** — Any change to running workflow code causes `NonDeterministicException`. Use `Workflow.getVersion()` from the first workflow written; replay tests in CI before every deployment.
2. **PHI in Temporal event history** — Pass only UUIDs through Temporal; Activities resolve full records from the app DB. Treat Temporal persistence as HIPAA-covered.
3. **Incomplete audit trail** — Append-only audit table with INSERT-only DB role; AOP interceptor captures all PHI-touching operations; must exist before first PHI feature.
4. **Alert fatigue** — Co-design every alert type and threshold with pilot practice nurses before writing detection code; track alert-to-action ratio.
5. **BAA gap with Claude API** — V1: zero PHI in Claude prompts. Initiate Anthropic BAA negotiation early (2-8 weeks). Maintain BAA register.

## Implications for Roadmap

### Phase 1: Foundation — HIPAA Infrastructure and Domain Model
**Rationale:** Everything depends on schema, audit trail, and RBAC; cannot be retrofitted.
**Delivers:** PostgreSQL schema + Flyway migrations; JPA entities with Envers `@Audited`; Spring Security + Keycloak OIDC JWT; three RBAC roles; AuditLoggingFilter; append-only audit table; pgcrypto column encryption; Docker Compose environment
**Avoids:** Pitfall 3 (incomplete audit trail), Pitfall 2 (PHI in Temporal)

### Phase 2: Temporal Infrastructure and Workflow Skeleton
**Rationale:** Temporal integration must be proven before business logic is built on it. Versioning discipline and PHI isolation rules must be in place before pathway code is written.
**Delivers:** TemporalConfig; worker registration; PatientPathwayWorkflow interface + no-op impl; Activity stubs; WorkflowReplayer replay test harness in CI; Continue-As-New skeleton
**Avoids:** Pitfall 1 (non-determinism), Pitfall 10 (Temporal operational complexity)

### Phase 3: Core Pathway Engine and Deviation Detection
**Rationale:** The dashboard has nothing to show until the engine produces deviation data. Clinical pathway modeling issues must surface before they multiply across three pathways.
**Delivers:** PatientPathwayWorkflowImpl with real state machine logic; timer-based detection for all three deviation types; pathway templates for breast/lung/colorectal; template-based alert text
**Avoids:** Pitfall 8 (linear pathway model), Pitfall 2 (event history explosion)

### Phase 4: REST API and Alert Management
**Rationale:** Controllers connect staff actions to the workflow engine. OpenAPI spec unblocks parallel frontend development.
**Delivers:** REST controllers for patients, care events, alerts; PathwayEnrollmentService; AlertService; alert resolution workflow; OpenAPI spec

### Phase 5: React Dashboard and Data Entry UX
**Rationale:** Data entry friction is the adoption bottleneck. Form UX must be tested with nurses before pilot.
**Delivers:** Prioritized patient worklist; pathway status view; alert queue dashboard; care event entry form; alert resolution modal; WebSocket push + polling fallback; responsive iPad layout
**Avoids:** Pitfall 9 (data entry abandonment)

### Phase 6: AI Alert Enhancement and Pre-Pilot Hardening
**Rationale:** Claude integration after BAA confirmed and core detection validated. AWS production deployment validated before pilot.
**Delivers:** ClaudeAlertGeneratorActivity; circuit breaker with template fallback; AWS deployment (ECS Fargate, RDS, Secrets Manager, CloudTrail, KMS); OWASP Dependency-Check in CI

### Phase Ordering Rationale
- HIPAA infrastructure first because it cannot be retrofitted without rewriting schema, permissions, and data flows
- Temporal skeleton before business logic because determinism and PHI isolation rules must be established before workflow code is written
- Core pathway engine before API/UI because the dashboard has nothing to show until the engine produces deviation data
- REST API before dashboard to provide the OpenAPI contract that drives parallel frontend development
- AI integration last because it depends on validated deviation detection and confirmed BAA status

### Research Flags

**Phases needing deeper research during planning:**
- **Phase 3:** Clinical pathway content requires oncologist co-authorship — domain knowledge, not software research
- **Phase 6:** Temporal self-hosted on ECS Fargate has limited documented production examples; Anthropic BAA negotiation timeline

**Phases with standard patterns (skip research-phase):**
- **Phase 1:** Spring Boot 3.5 + Keycloak + Flyway + Hibernate Envers is well-documented
- **Phase 4:** Standard Spring MVC + Spring Security patterns
- **Phase 5:** TanStack Query + TanStack Router + shadcn/ui are mainstream 2025/2026 stack

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Core versions verified via official release pages and MVNRepository |
| Features | HIGH | Table stakes derived from clinical literature and competitor products |
| Architecture | HIGH | Temporal Spring Boot patterns verified via official docs |
| Pitfalls | HIGH | Non-determinism, event history, PHI isolation from official Temporal references; HIPAA from HHS.gov |

**Overall confidence:** HIGH

### Gaps to Address

- **Clinical pathway content:** Pathway templates for breast/lung/colorectal require oncologist authorship. Block Phase 3 on clinically validated content.
- **Pilot nurse co-design:** Alert threshold co-design requires access to pilot practice nurses. Schedule before Phase 5 completes.
- **Anthropic BAA:** Initiate in Phase 1-2 even though Claude integration is Phase 6. Negotiations take 2-8 weeks.
- **Temporal on ECS Fargate:** Limited public examples. Validate with deployment spike in Phase 2.

---
*Research completed: 2026-04-29*
*Ready for roadmap: yes*
