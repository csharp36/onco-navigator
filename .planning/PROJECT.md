# Onco-Navigator AI

## What This Is

Onco-Navigator AI is a HIPAA-compliant care pathway monitoring system for medical oncology practices. It tracks patient journeys across multiple external facilities (surgery, radiology, pathology labs), detects when care events are missing, delayed, or out of sequence, and alerts nurse navigators with plain-language descriptions and suggested corrective actions. The nurse decides and acts — the AI monitors and suggests.

## Core Value

Prevent patients from falling through the cracks by systematically watching every patient's care pathway and surfacing deviations before they become wasted visits, delayed treatments, or invisible gaps.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Pathway monitoring engine that evaluates patient state against defined care sequences
- [ ] Deviation detection: missing events, delayed events, out-of-order events
- [ ] Nurse-facing dashboard with alert management and patient pathway visualization
- [ ] Manual patient and care event data entry (no EMR integration in V1)
- [ ] 3 cancer pathways: breast cancer, lung cancer, colorectal cancer
- [ ] Role-based access control (nurse navigator, care coordinator, administrator)
- [ ] Immutable audit trail for all data access and system actions
- [ ] HIPAA-compliant encryption at rest and in transit
- [ ] Alert resolution workflow with documentation
- [ ] Claude API integration for non-standard deviation alert generation
- [ ] Configurable pathway templates (admin can define step sequences, time windows, prerequisites)
- [ ] Spring profiles for local Docker and AWS deployment

### Out of Scope

- EMR integration (automatic data ingestion) — Phase 2, requires API access negotiations with EMR vendor
- SMS/notification delivery — deferred to Phase 2, dashboard-only alerts for V1
- Patient-facing communication — V1 is staff-only, no patient contact
- Autonomous clinical actions — non-negotiable human-in-the-loop design
- Billing, insurance, or clinical documentation — outside the coordination problem
- Symptom monitoring or clinical assessments — not the practice's coordination role
- Mobile native apps — responsive web dashboard covers tablet/phone use

## Context

This project is a collaboration between a software engineer (with deep cryptography/key management expertise) and a medical oncologist neighbor who wrote the concept brief and feature specification. The doctor understands the clinical problem intimately — medical oncology practices are coordination hubs where key events (surgery, imaging, biopsy, pathology) happen at external facilities, and the practice must track completion and results. Today this tracking is done manually, from memory, by nurse navigators juggling dozens of patients.

The concept brief and V1 feature specification are in `docs/` and define:
- Detailed pathway examples (breast cancer walkthrough is essentially a state machine in prose)
- Three pathway definitions with step-by-step trigger conditions and alert text
- Six example alert scenarios usable as test cases
- Success metrics: >80% alert accuracy, <4hr resolution time, 99% uptime

The doctor does not use engineering terminology but has described, in effect: a workflow orchestration system with durable state machines, event-driven deviation detection, an escalation pipeline, and a rules engine for corrective action suggestions.

## Constraints

- **HIPAA Compliance**: All architecture decisions must satisfy HIPAA requirements from day one — encryption, audit logging, access control, BAA readiness. Not retrofitted.
- **Human-in-the-Loop**: AI monitors and suggests. Nurses and physicians decide and act. Non-negotiable for clinical safety, regulatory simplicity, and trust-building.
- **No EMR Connection in V1**: Staff enters data manually. The data entry UX is critical — if it's painful, staff won't use it, and the system has no data.
- **Proof of Concept at Single Practice**: V1 targets one pilot practice. Scale concerns are deferred but architecture should not preclude scaling.
- **Tech Stack**: Java 21 + Spring Boot 3, Temporal.io (self-hosted, all-in), PostgreSQL, React + TypeScript, Docker Compose for local dev, AWS-ready via Spring profiles.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Java/Spring Boot backend | Developer's primary expertise, strong enterprise ecosystem, excellent Temporal SDK | — Pending |
| Temporal.io for workflow orchestration | Patient pathways are long-running durable workflows (weeks/months) with timers, prerequisites, and crash recovery. Temporal handles this natively. Self-hosted, MIT licensed, no cost. | — Pending |
| PostgreSQL | Rock-solid relational DB, good JSON support, HIPAA-friendly, natural fit for structured patient/event data | — Pending |
| React + TypeScript frontend | Dashboard needs interactivity (alert cards, pathway visualization, real-time updates). Largest ecosystem for component libraries. | — Pending |
| All-in on Temporal (not Spring State Machine) | No stubs or half-measures. Real durable workflows from day one with timers, retries, and state recovery. | — Pending |
| Defer SMS to Phase 2 | Dashboard-only alerts for PoC. Reduces vendor dependencies and scope. SMS (Twilio) added when moving to pilot. | — Pending |
| Claude API for edge-case alerts | Template strings for known deviations (predictable, no cost). Claude API for non-standard situations where canned text doesn't fit. | — Pending |
| Pilot-ready from day one | Build HIPAA compliance into architecture from the start. Encryption at rest, audit trails, RBAC, key management. Retrofitting is more total work. | — Pending |
| Spring profiles for deployment | `local` profile runs everything in Docker Compose. `aws` profile targets cloud deployment. Same codebase, different config. | — Pending |
| All 3 pathways in V1 | Build the engine config-driven so pathways are data, not code. Ship with breast, lung, and colorectal cancer pathways to prove generalization. | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-29 after initialization*
