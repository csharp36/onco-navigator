# Onco-Navigator AI

A HIPAA-compliant care pathway monitoring system for medical oncology practices.

## The Problem

Medical oncology practices are coordination hubs where key care events — surgery, imaging, biopsy, pathology reports — happen at external facilities. The practice must track completion, detect delays, and ensure nothing falls through the cracks. Today this tracking is done manually, from memory, by nurse navigators juggling dozens of patients simultaneously.

Patients get lost. Referrals go unanswered. Biopsy results sit in a queue for days. A surgeon operates without the pathology report. These aren't rare edge cases — they're the daily reality of multi-facility cancer care coordination.

## The Solution

Onco-Navigator AI systematically watches every patient's care pathway and surfaces deviations before they become wasted visits, delayed treatments, or invisible gaps. It provides:

- **Pathway Monitoring** — Each patient is enrolled in a cancer-specific care pathway (breast, lung, colorectal). The system tracks expected steps, time windows, and prerequisites using durable Temporal.io workflows that survive restarts and run for weeks/months.

- **Deviation Detection** — Three types of alerts are generated automatically:
  - **Missing Event** — A required pathway step has no completed care event within the configured time window
  - **Delayed Event** — Elapsed time since the previous step exceeds the pathway's threshold
  - **Out of Order** — A care event is recorded before its prerequisite steps are completed

- **Alert Management** — Nurse navigators see all open alerts sorted by clinical severity (overdue first, then missing, then out-of-order). They can drill into patient pathway status, see exactly which step is affected, and resolve alerts with documentation notes.

- **Human-in-the-Loop** — The AI monitors and suggests. Nurses and physicians decide and act. This is non-negotiable for clinical safety, regulatory simplicity, and trust-building.

## Architecture

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Backend | Java 21 + Spring Boot 3.5 | REST API, business logic, security |
| Workflow Engine | Temporal.io (self-hosted) | Durable pathway monitoring, timers, crash recovery |
| Database | PostgreSQL 16 | Patient data, events, alerts (column-encrypted PHI) |
| Identity | Keycloak 26 | OIDC authentication, role-based access |
| Frontend | React 19 + TypeScript + Vite | Nurse dashboard, patient management |
| UI | shadcn/ui + Tailwind CSS v4 | Component library, responsive design |

### Security (HIPAA)

- AES-256-GCM column encryption for all PHI fields (names, DOB, MRN)
- HMAC-SHA256 index tokens for deterministic encrypted field search
- Hibernate Envers immutable audit trail on all ePHI entities
- Keycloak JWT authentication with method-level RBAC (`@PreAuthorize`)
- PHI redaction in all log output (UUID-only logging)
- Separate encryption and HMAC keys (key separation principle)

## Getting Started

### Prerequisites

- Java 21 (JDK)
- Node.js 20+
- Docker Desktop

### 1. Start Infrastructure

```bash
# Copy environment file
cp .env.example .env

# Start PostgreSQL, Temporal, Keycloak
docker compose up -d
```

Wait ~30 seconds for all services to initialize. Keycloak imports the realm automatically.

### 2. Start Backend

```bash
SPRING_PROFILES_ACTIVE=local JASYPT_ENCRYPTOR_PASSWORD=localdev_jasypt_master_key_change_me ./mvnw spring-boot:run
```

The backend starts on port **8081**. Flyway runs migrations automatically on startup.

### 3. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend starts on port **5173** with a proxy to the backend API.

### 4. Log In

Navigate to http://localhost:5173. You'll be redirected to Keycloak for authentication.

| Username | Password | Role | Can Do |
|----------|----------|------|--------|
| coordinator1 | coordinator1 | Care Coordinator | Create patients, record care events, view pathways |
| nurse1 | nurse1 | Nurse Navigator | View/resolve alerts, view patient pathways |
| admin1 | admin1 | Administrator | All of the above |

## Development Ports

| Service | Port | URL |
|---------|------|-----|
| Frontend (Vite) | 5173 | http://localhost:5173 |
| Backend (Spring Boot) | 8081 | http://localhost:8081 |
| Keycloak Admin | 9090 | http://localhost:9090/admin (admin/see .env) |
| Temporal Web UI | 8080 | http://localhost:8080 |
| PostgreSQL | 5432 | `psql -h localhost -U postgres` |
| Temporal gRPC | 7233 | Used by Spring Boot internally |

## Project Structure

```
.
├── src/main/java/com/onconavigator/
│   ├── domain/           # JPA entities (Patient, Alert, CareEvent, PathwayTemplate)
│   ├── repository/       # Spring Data JPA repositories
│   ├── service/          # Business logic (PatientService, AlertService, PathwayStatusService)
│   ├── web/              # REST controllers + DTOs + GlobalExceptionHandler
│   ├── workflow/         # Temporal workflow implementations
│   ├��─ activity/         # Temporal activity implementations (deviation detection)
│   ├── security/         # SecurityConfig, HmacTokenService, EncryptionConverter
│   └── config/           # Spring configuration classes
├── src/main/resources/
│   ├── db/migration/     # Flyway SQL migrations (V1-V8)
│   └── application-local.yml  # Local dev configuration
├── frontend/
│   ├── src/
│   │   ├── routes/       # TanStack Router file-based routes
│   │   ├── features/     # Feature modules (patients/, alerts/, dashboard/)
│   │   ├── components/   # shadcn/ui components + layout
│   │   └── lib/          # API client, auth utilities
│   └── package.json
├── docker-compose.yml    # Local dev infrastructure
├── keycloak/             # Realm export for auto-import
├── docker/               # DB initialization scripts
└── docs/                 # Concept brief and feature specification
```

## Key Workflows

### Patient Enrollment

1. Care coordinator creates patient via two-step wizard (demographics + clinical info)
2. Backend computes HMAC token for MRN search, encrypts PHI, saves to PostgreSQL
3. Temporal pathway workflow starts automatically based on cancer type
4. Workflow begins monitoring for expected care events with configurable time windows

### Deviation Detection

1. Temporal workflow timers fire when expected care events are overdue
2. Activity evaluates patient's care events against pathway template
3. If deviation found: alert created with type, severity, description, and suggested action
4. Duplicate detection prevents redundant alerts for the same patient/step

### Alert Resolution

1. Nurse navigator views alert queue sorted by severity
2. Clicks through to patient pathway visualization to understand context
3. Takes corrective action (calls external facility, reschedules, etc.)
4. Resolves alert with documentation notes (min 10 characters)

## Cancer Pathways

Three pathways are pre-configured as JSONB templates in the database:

- **Breast Cancer** — Referral, consultation, biopsy, pathology, imaging, surgery, chemo/radiation, follow-up
- **Lung Cancer** — Referral, imaging, biopsy, pathology, staging, treatment planning, surgery/chemo/radiation
- **Colorectal Cancer** — Referral, colonoscopy, biopsy, pathology, imaging, surgery, chemo, follow-up

Each step defines: expected event type, time window (days), prerequisites, and whether it's required.

## Roadmap

- **Phase 1** (Complete) — HIPAA foundation: encryption, audit, RBAC, infrastructure
- **Phase 2** (Complete) — Pathway engine: Temporal workflows, deviation detection, pathway templates
- **Phase 3** (Complete) — Working application: REST API, dashboard, patient management, alert queue
- **Phase 4** (Next) — AI enhancement: Claude API for non-standard deviation alerts + AWS deployment

## License

Private — all rights reserved.
