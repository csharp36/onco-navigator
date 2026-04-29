# Stack Research

**Domain:** HIPAA-compliant healthcare care pathway monitoring system
**Researched:** 2026-04-29
**Confidence:** HIGH (core stack); MEDIUM (AWS deployment layer); HIGH (frontend)

---

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Java | 21 LTS | Runtime | LTS through Sept 2029. Virtual threads (Loom) are GA, reducing thread-per-request overhead. Temporal SDK explicitly supports virtual threads via PR #2297. |
| Spring Boot | 3.5.x | Application framework | Released May 2025. Managed dependency BOM handles Spring Security, Spring Data JPA, Actuator, and OAuth2 starters coherently. SSL Bundles (3.1+) simplify cert management. Virtual thread support via `spring.threads.virtual.enabled=true`. |
| Temporal Java SDK | 1.31–1.32 | Durable workflow orchestration | `temporal-spring-boot-starter` 1.32.0 is current. Auto-discovers `@WorkflowImpl`/`@ActivityImpl` beans and registers workers. Handles long-running pathway timers (days/weeks) with built-in retry, durable state, and crash recovery that Spring State Machine cannot match. |
| Temporal Server | 1.28.x | Self-hosted orchestration engine | `temporalio/auto-setup:v1.28.3` for Docker Compose dev; `temporalio/server:v1.28.3` for production. Uses PostgreSQL as persistence backend — the same DB the app uses, reducing operational surface. |
| PostgreSQL | 16.x | Primary data store | Rock-solid ACID guarantees for ePHI. JSON support for flexible event payloads. Column-level encryption via `pgcrypto` for ePHI fields. Temporal's persistence layer is PostgreSQL-native — no separate Cassandra required. |
| Spring Security | 6.x (via Boot BOM) | Authentication, authorization, HTTPS | Built into Spring Boot 3. Provides JWT/OIDC resource server, method-level `@PreAuthorize`, and CSRF/CORS control. Do not import version independently — use Boot BOM. |
| Keycloak | 26.x | Identity provider / OIDC auth server | Open-source, self-hostable. Issues JWT access tokens with role claims (`realm_access.roles`). Spring Security validates tokens without calling Keycloak on every request. Dockerized, works in both local Compose and AWS ECS. Replaces the need to build a custom auth server. |
| React | 19.x | Frontend SPA framework | Largest ecosystem for dashboard UIs. Server state management via TanStack Query. shadcn/ui built on React 19. |
| TypeScript | 5.x | Frontend type safety | End-to-end type safety. Required for TanStack Query generics and Zod schema inference. |
| Vite | 6.x | Frontend build tool | Fastest dev server / HMR. Standard choice for React+TypeScript in 2025. Replaces CRA (deprecated). |

---

### Supporting Libraries

#### Backend — Spring Boot Layer

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-boot-starter-data-jpa` | via Boot BOM | Hibernate ORM + Spring Data repositories | All entity persistence |
| `hibernate-envers` | via Boot BOM | Immutable entity revision audit trail | Annotate all ePHI-touching entities with `@Audited`. Envers creates `_AUD` tables with revision numbers — satisfies HIPAA audit requirement without custom code. |
| `spring-boot-starter-oauth2-resource-server` | via Boot BOM | JWT validation against Keycloak JWKS | All REST endpoints that carry ePHI |
| `spring-boot-starter-actuator` | via Boot BOM | Health, metrics, and audit event endpoints | Expose `/actuator/health` (used by Docker/ECS health checks) and `/actuator/auditevents` |
| `flyway-core` | 11.x (via Boot BOM) | SQL schema migrations | All schema changes. Versioned SQL files, auto-applied on startup. |
| `flyway-database-postgresql` | 11.x (explicit) | PostgreSQL dialect for Flyway 10+ | Required separately from `flyway-core` — Flyway 10+ moved DB drivers to separate modules. Do not omit this. |
| `postgresql` JDBC driver | 42.7.x (via Boot BOM) | JDBC connectivity | Runtime scope only |
| `spring-ai-anthropic-spring-boot-starter` | 1.1.0 (GA) | Claude API integration | Non-standard deviation alert generation. GA since May 2025. Wraps the official Anthropic Java SDK. Supports claude-3-5-sonnet and claude-3-7-sonnet models. |
| `jasypt-spring-boot-starter` | 3.0.5 | Encrypted config property values | Local dev only — encrypt DB passwords in `application-local.properties`. On AWS, use Secrets Manager instead. |
| `spring-boot-starter-validation` | via Boot BOM | Bean validation (JSR-380) | Input validation on all REST request bodies — critical for preventing PHI corruption |

#### Backend — Temporal Worker Layer

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `io.temporal:temporal-spring-boot-starter` | 1.32.0 | Spring Boot autoconfiguration for Temporal workers and WorkflowClient | Single dependency that replaces manual WorkerFactory setup. Scan packages with `spring.temporal.workers-auto-discovery.packages`. |
| `io.temporal:temporal-sdk` | included transitively | Core Temporal workflow/activity DSL | Pulled in by the starter; do not declare separately unless overriding version. |

#### Frontend

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `@tanstack/react-query` | v5.x | Server state (API fetching, caching, background refresh) | All REST API calls. Do not use `useEffect` + `fetch` for data fetching. |
| `@tanstack/react-table` | v8.x | Patient list and alert queue table | Sortable, filterable tables with virtual scrolling for large patient lists. |
| `@tanstack/react-router` | v1.x | Type-safe client-side routing | Replace react-router-dom. File-based routing, loaders that co-locate data fetching with route. |
| `shadcn/ui` | current | Copy-paste component primitives (Radix UI + Tailwind) | Alert cards, dialogs, badges, form inputs. Not a npm package — components are added via CLI and owned by the project. |
| `tailwindcss` | v4.x | Utility CSS | Required by shadcn/ui. Use OKLCh color tokens for theming. |
| `recharts` | v3.x | Data visualization | Pathway timeline charts, alert trend graphs. shadcn/ui's Chart component is built on Recharts v3. |
| `react-hook-form` | v7.x | Uncontrolled form state | Manual care event data entry forms — critical UX path for V1. Pairs with Zod for validation. |
| `zod` | v3.x | Schema validation (frontend) | Validates form inputs and API response shapes. Integrate with react-hook-form via `@hookform/resolvers/zod`. |
| `zustand` | v5.x | Client-side UI state | Dashboard filter state, selected patient, open panels. Do not use for server data (that's TanStack Query's job). |
| `date-fns` | v4.x | Date manipulation | Care timeline calculations, event delay computation, display formatting. |

---

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Docker Compose | Local dev environment orchestration | Run PostgreSQL, Keycloak, Temporal Server (auto-setup), and the Spring Boot app together. Use `temporalio/auto-setup:v1.28.3` image — it configures Temporal's schema against PostgreSQL automatically. |
| `temporalio/ui` | Temporal Web UI (Docker) | `temporalio/ui:latest` at port 8080. Inspect workflow execution history, debug stuck workflows. |
| Maven Wrapper (`./mvnw`) | Reproducible builds | Pin Maven version in `.mvn/wrapper/maven-wrapper.properties`. Do not require local Maven install. |
| Checkstyle / SpotBugs | Static analysis | Catch unencrypted string logging (PHI leakage) at build time. Configure custom rules for PHI field names. |
| OWASP Dependency-Check | CVE scanning | Add to Maven build. Critical for HIPAA — known-vulnerable dependencies are a compliance finding. |
| Postman / Bruno | API testing during dev | Bruno is offline-first and git-friendly (stores collections as files). Postman requires cloud sync — avoid with ePHI test data. |
| Testcontainers | Integration testing | Spin up real PostgreSQL and Temporal containers in tests. Avoid mocking the database in HIPAA systems — schema and constraint bugs matter. |

---

## Installation

### Backend (`pom.xml` key dependencies)

```xml
<!-- Spring Boot BOM — all Spring libraries version from here -->
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.5.0</version>
</parent>

<!-- Core web + security -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>

<!-- Data -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <scope>runtime</scope>
</dependency>

<!-- Audit (HIPAA-required) -->
<dependency>
  <groupId>org.hibernate.orm</groupId>
  <artifactId>hibernate-envers</artifactId>
</dependency>

<!-- Schema migrations — BOTH required for Flyway 10+ with PostgreSQL -->
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-postgresql</artifactId>
</dependency>

<!-- Temporal workflow orchestration -->
<dependency>
  <groupId>io.temporal</groupId>
  <artifactId>temporal-spring-boot-starter</artifactId>
  <version>1.32.0</version>
</dependency>

<!-- Claude API via Spring AI -->
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-anthropic-spring-boot-starter</artifactId>
  <version>1.1.0</version>
</dependency>

<!-- Operations -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

### Frontend

```bash
# Scaffold
npm create vite@latest onco-navigator-ui -- --template react-ts

# Server state + routing + tables
npm install @tanstack/react-query @tanstack/react-router @tanstack/react-table

# UI primitives (shadcn/ui installs via CLI, not npm install)
npx shadcn@latest init
npx shadcn@latest add button card badge dialog table alert

# Charts
npm install recharts

# Forms + validation
npm install react-hook-form zod @hookform/resolvers

# Client state + date utils
npm install zustand date-fns

# CSS
npm install tailwindcss @tailwindcss/vite
```

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not Alternative |
|----------|-------------|-------------|---------------------|
| Workflow orchestration | Temporal.io | Spring State Machine | Spring State Machine loses state on restart, has no durable timers, and cannot recover from crashes. Patient pathways last weeks — durable execution is non-negotiable. Temporal is already a key decision in PROJECT.md. |
| Identity provider | Keycloak (self-hosted) | Spring Authorization Server | Spring Authorization Server requires building user management UI from scratch. Keycloak provides a complete admin UI, user federation, and production-grade session management out of the box. For a pilot with 3 roles and a handful of users, Keycloak is faster and safer. |
| Identity provider | Keycloak (self-hosted) | Auth0 / Okta | SaaS IdPs require a BAA with the vendor before storing user identities linked to ePHI contexts. Keycloak is self-hosted — no third-party BAA needed for V1 pilot. |
| Claude integration | Spring AI 1.1.0 | Direct `anthropic-java` SDK | Spring AI provides a consistent `ChatClient` abstraction. If the model needs to change (Claude → GPT-4 fallback), the abstraction doesn't need to change. Spring AI 1.1.0 is GA and wraps the official Anthropic Java SDK internally. |
| Frontend routing | TanStack Router | React Router v7 | TanStack Router is fully type-safe (route params, search params). In a dashboard that links to patient detail views with complex query params, type safety on routes prevents silent bugs. React Router v7 added some typing but is not as comprehensive. |
| CSS/component library | shadcn/ui + Tailwind | Material UI (MUI) | MUI imposes a visual language that looks clinical but has heavy bundle sizes and complex theming. shadcn/ui components are owned by the project — full control over accessibility and styling. |
| Database migrations | Flyway | Liquibase | Flyway uses plain SQL migration files — straightforward, reviewable, no XML/YAML DSL. Liquibase's flexibility adds complexity that isn't needed for a single-DB application. |
| Secrets (local dev) | Jasypt encrypted properties | Plaintext `application-local.properties` | Developers should not commit plaintext DB passwords even in gitignored files. Jasypt encrypts values with a runtime master key. On AWS, replace with Secrets Manager — Jasypt is local-only. |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Keycloak Spring Boot Adapter (`keycloak-spring-boot-adapter`) | Deprecated since Keycloak 17. Removed from active support. Causes runtime errors with Spring Boot 3. | `spring-boot-starter-oauth2-resource-server` + Keycloak as OIDC/JWT issuer |
| `spring.datasource.url` with plaintext credentials in committed files | PHI data source credentials in version control are a HIPAA violation trigger | Jasypt for local dev; AWS Secrets Manager with Spring Cloud AWS (`io.awspring.cloud:spring-cloud-aws-starter-secrets-manager:3.3.0`) for prod |
| `Workflow.sleep()` inside activities | Activities must be idempotent and stateless. Sleep inside an activity blocks a thread and is not durable. | Use `Workflow.newTimer(Duration)` inside the workflow class — this is Temporal's durable sleep. |
| Logging PHI fields (patient name, DOB, diagnosis) | Direct HIPAA violation. PHI in logs is PHI in plaintext. | Log patient IDs (UUIDs) and event type codes only. Configure Logback to redact any string matching known PHI field names. |
| `flyway-core` alone (without `flyway-database-postgresql`) | Flyway 10+ split PostgreSQL support into a separate module. Using only `flyway-core` causes `Unsupported Database: PostgreSQL` on startup. | Both `flyway-core` and `flyway-database-postgresql` are required. |
| React Router v5/v6 | TanStack Router offers end-to-end type safety on route params and search params that React Router does not. Mismatched route params are a source of silent bugs in patient navigation. | `@tanstack/react-router` v1.x |
| `useEffect` + `fetch` for API calls | Manual effect-based fetching has no caching, no background refresh, no deduplication, and no loading state management. | TanStack Query `useQuery` / `useMutation` |
| Spring Boot 2.x | End of OSS support November 2023. Will not receive Spring Security or dependency CVE patches — a direct compliance risk. | Spring Boot 3.5.x |

---

## Stack Patterns by Variant

**Local development (`spring.profiles.active=local`):**
- PostgreSQL: Docker Compose service on port 5432
- Temporal: `temporalio/auto-setup:v1.28.3` on port 7233, UI on port 8080
- Keycloak: Docker Compose on port 9090, H2 persistence (no separate DB needed for dev Keycloak)
- Secrets: Jasypt-encrypted values, master key as env var in `.env` (gitignored)
- TLS: Self-signed cert or HTTP (not in scope for local)

**AWS production (`spring.profiles.active=aws`):**
- PostgreSQL: RDS PostgreSQL 16.x with encryption-at-rest (KMS CMK), Multi-AZ
- Temporal: Self-hosted on ECS Fargate or EC2 with RDS as persistence backend
- Keycloak: ECS Fargate, RDS for persistence, ALB in front
- Spring Boot API: ECS Fargate behind Application Load Balancer (ALB with ACM certificate for TLS termination)
- Secrets: AWS Secrets Manager, injected via Spring Cloud AWS (`spring.config.import: aws-secretsmanager:/`)
- KMS: Customer-managed key (CMK) for RDS encryption, S3 server-side encryption
- CloudTrail: Mandatory for HIPAA — captures all AWS API calls for audit
- VPC: All services in private subnets. No direct public internet access to DB or Temporal.
- BAA: AWS BAA must be executed before any ePHI enters the account. All services used (RDS, ECS, Secrets Manager, KMS, CloudTrail, ALB, S3, CloudWatch Logs) are HIPAA-eligible.

**PostgreSQL column encryption for ePHI (pgcrypto):**
- Use `pgp_sym_encrypt()` / `pgp_sym_decrypt()` for columns containing PHI fields (SSN, DOB, contact info if stored)
- Application-layer encryption via `@Convert` JPA attribute converter wrapping AES-GCM via Java's `javax.crypto`
- Note: Native PostgreSQL TDE is not in community PostgreSQL 16 (Percona's `pg_tde` extension exists as open source alternative but is immature). Prefer storage-level encryption via AWS RDS KMS + column-level encryption for sensitive fields.

---

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| `temporal-spring-boot-starter:1.32.0` | Spring Boot 3.x, Java 17+ | Java 21 virtual thread support added. Tested against Spring Boot 3.4/3.5. |
| `spring-ai-anthropic-spring-boot-starter:1.1.0` | Spring Boot 3.x | GA since Nov 2025. Spring AI 2.0.0-M1 requires Spring Boot 4 — do not use with Boot 3. |
| `flyway-core:11.x` + `flyway-database-postgresql:11.x` | Spring Boot 3.5 BOM | Boot 3.5 manages Flyway 11.x. Both JARs required — this is a breaking change from Flyway 9. |
| `hibernate-envers` | Spring Boot 3.5 BOM / Hibernate 6.x | Pulled in via Boot BOM when declared. Hibernate 6 (not 5) is required — Boot 3.x uses Hibernate 6. |
| `react:19.x` + `recharts:3.x` | Requires `react-is` override for recharts peer dep | Add `overrides: { "react-is": "^19.0.0" }` in `package.json` to satisfy recharts' peer dep on react-is. |
| `@tanstack/react-query:v5` | React 18+ and React 19 | v5 API is not backward-compatible with v4 (no `cacheTime`, renamed to `gcTime`). Do not upgrade from v4 without reading migration guide. |
| Keycloak `26.x` | Spring Boot 3 OAuth2 resource server | Use `spring.security.oauth2.resourceserver.jwt.issuer-uri` pointing to Keycloak realm. Do NOT use the deprecated Keycloak Spring Boot adapter. |

---

## HIPAA Technical Safeguard Checklist (Stack Implementation)

| Requirement | Implementation |
|-------------|----------------|
| Encryption in transit | Spring Boot SSL Bundles (TLS 1.3); ALB with ACM cert in prod; Temporal with TLS on port 7233 in prod |
| Encryption at rest | RDS KMS CMK; `pgcrypto` column encryption for ePHI fields; Temporal's PostgreSQL persistence inherits RDS encryption |
| Access control | Keycloak OIDC + Spring Security method-level `@PreAuthorize`; roles: `ROLE_NURSE_NAVIGATOR`, `ROLE_CARE_COORDINATOR`, `ROLE_ADMIN` |
| Audit controls | Hibernate Envers `@Audited` on all ePHI entities; Spring Actuator `auditevents`; AWS CloudTrail for infrastructure |
| PHI minimization in logs | Logback configuration blocking PHI field names; log patient UUIDs only |
| Secrets management | Jasypt (local); AWS Secrets Manager + KMS (prod); no plaintext credentials in repository |
| BAA coverage | AWS BAA covers all HIPAA-eligible services used; Keycloak and Temporal are self-hosted (no external BAA needed) |

---

## Sources

- [Temporal Spring Boot integration docs](https://docs.temporal.io/develop/java/spring-boot-integration) — worker autoconfiguration, namespace config (MEDIUM confidence, doc version 1.31.0)
- [temporal-spring-boot-starter on MVNRepository](https://mvnrepository.com/artifact/io.temporal/temporal-spring-boot-starter) — version 1.32.0 confirmed (HIGH)
- [Spring Boot 3.5.0 release announcement](https://spring.io/blog/2025/05/22/spring-boot-3-5-0-available-now/) — released May 2025 (HIGH)
- [Spring AI 1.1 GA release](https://spring.io/blog/2025/11/12/spring-ai-1-1-GA-released/) — stable Anthropic integration (HIGH)
- [Spring AI Anthropic Chat docs](https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html) — model IDs, configuration (HIGH)
- [Temporal server Docker Hub](https://hub.docker.com/r/temporalio/server) — v1.28.3 latest (MEDIUM, as of research date)
- [AWS HIPAA eligible services](https://www.accountablehq.com/post/how-to-get-a-baa-with-aws-steps-requirements-and-covered-hipaa-services) — RDS, ECS, Secrets Manager, KMS confirmed (HIGH)
- [Spring Cloud AWS docs](https://docs.awspring.io/spring-cloud-aws/docs/current/reference/html/index.html) — Secrets Manager integration 3.3.0 (HIGH)
- [Flyway + Spring Boot 3 PostgreSQL guide](https://blog.jetbrains.com/idea/2024/11/how-to-use-flyway-for-database-migrations-in-spring-boot-applications/) — flyway-database-postgresql requirement (HIGH)
- [PostgreSQL encryption options (official docs)](https://www.postgresql.org/docs/current/encryption-options.html) — pgcrypto, TDE status (HIGH)
- [Keycloak Spring Boot RBAC guide](https://developers.redhat.com/articles/2023/07/24/how-integrate-spring-boot-3-spring-security-and-keycloak) — adapter deprecation, resource server pattern (HIGH)
- Context7: `/temporalio/sdk-java` — Worker configuration, retry options, timer patterns (HIGH)
- Context7: `/websites/spring_io_spring-boot_3_5` — OAuth2, SSL autoconfiguration (HIGH)

---

*Stack research for: HIPAA-compliant oncology care pathway monitoring system (Onco-Navigator AI)*
*Researched: 2026-04-29*
