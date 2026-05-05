---
phase: 08-template-inheritance
plan: 01
subsystem: pathway-templates
tags: [template-inheritance, merge-engine, flyway, dto, tdd]
dependency_graph:
  requires: []
  provides: [template-inheritance-schema, template-merge-engine, rectal-template-seed]
  affects: [PathwayForkService, PathwayTemplateRepository]
tech_stack:
  added: []
  patterns: [diff-based-inheritance, pure-function-merge, self-referential-fk]
key_files:
  created:
    - src/main/resources/db/migration/V19__template_inheritance.sql
    - src/main/resources/db/migration/V20__seed_rectal_template.sql
    - src/main/java/com/onconavigator/domain/dto/TemplateDiff.java
    - src/main/java/com/onconavigator/domain/dto/StepOverride.java
    - src/main/java/com/onconavigator/domain/dto/EdgeChanges.java
    - src/main/java/com/onconavigator/domain/dto/EdgeRef.java
    - src/main/java/com/onconavigator/service/TemplateMergeService.java
    - src/test/java/com/onconavigator/domain/dto/TemplateDiffTest.java
    - src/test/java/com/onconavigator/service/TemplateMergeServiceTest.java
  modified:
    - src/main/java/com/onconavigator/domain/PathwayTemplate.java
    - src/main/java/com/onconavigator/repository/PathwayTemplateRepository.java
    - src/main/java/com/onconavigator/service/PathwayForkService.java
decisions:
  - "Edge changes in rectal template use CRC_01->CRC_03 removal (matching actual V6 data) not CRC_02->CRC_03 (plan text deviation from actual edge structure)"
  - "PathwayForkService updated to use findByCancerTypeAndParentTemplateIdIsNull immediately (callers fix, not deferred)"
metrics:
  duration: 8min
  completed: "2026-05-05T20:12:00Z"
  tasks: 2
  files: 12
---

# Phase 08 Plan 01: Template Inheritance Schema and Merge Engine Summary

Flyway migrations for parent_template_id/name/description columns, four DTO records for diff-based inheritance, and a stateless merge engine resolving parent + child diff with edge integrity validation.

## What Was Built

### Task 1: Schema, Entity, DTOs, Repository
- **V19 migration**: Drops UNIQUE on cancer_type, adds parent_template_id FK, name (NOT NULL after backfill), description columns to pathway_templates and pathway_templates_aud
- **V20 migration**: Seeds rectal cancer child template with neoadjuvant chemoradiation diff (override CRC_03 windowDays to 60, add RECTAL_01 step, reroute edges through neoadjuvant)
- **PathwayTemplate entity**: Added parentTemplateId, name, description fields; removed unique=true from cancerType annotation
- **TemplateDiff record**: Top-level diff structure with null-safe compact constructor
- **StepOverride record**: stepId + fields map for partial step overrides
- **EdgeChanges record**: remove/add lists with null-safe compact constructor
- **EdgeRef record**: from/to stepId pair for edge references
- **PathwayTemplateRepository**: findByCancerType returns List, added findByCancerTypeAndParentTemplateIdIsNull and findByParentTemplateId
- **PathwayForkService**: Updated caller to use root template lookup method

### Task 2: TemplateMergeService
- **Merge algorithm**: removals -> overrides -> additions -> edge changes -> validation -> renumber
- **applyOverride**: Field-by-field construction with type-safe casting (Number, String, Boolean, enum valueOf)
- **applyEdgeChanges**: Modifies prerequisites based on EdgeRef from/to matching
- **validatePrerequisites**: Removes dangling references with WARN log (no failure)
- **withStepNumber**: Sequential renumbering after all operations
- **11 unit tests total**: 3 DTO tests + 8 merge engine tests including combined rectal scenario

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Corrected edge change in V20 seed data**
- **Found during:** Task 1 (V20 creation)
- **Issue:** Plan text specified `remove {"from": "CRC_02", "to": "CRC_03"}` but V6 seed data shows CRC_03 has prerequisites `["CRC_01"]` not `["CRC_02"]`. No edge exists from CRC_02 to CRC_03.
- **Fix:** Used `remove {"from": "CRC_01", "to": "CRC_03"}` matching actual data (consistent with RESEARCH.md Pattern 1)
- **Files modified:** V20__seed_rectal_template.sql
- **Commit:** 73f2456

## TDD Gate Compliance

- RED gate: fcdf435 (test: TemplateDiff DTOs), db434d5 (test: TemplateMergeService)
- GREEN gate: 73f2456 (feat: schema/DTOs/repository), 57dad3c (feat: merge engine)
- REFACTOR gate: not needed (code is clean as-is)

## Verification Results

- `./mvnw compile -pl . -q` exits 0 (clean compile)
- `./mvnw test -Dtest=TemplateMergeServiceTest,TemplateDiffTest` exits 0 (11 tests pass)
- V19 valid SQL (DROP CONSTRAINT, ALTER TABLE, CREATE INDEX, GRANT)
- V20 uses subquery for parent_template_id (not hardcoded UUID)

## Self-Check: PASSED

All 9 created files verified on disk. All 4 commits verified in git log.
