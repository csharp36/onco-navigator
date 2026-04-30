---
phase: 02-pathway-engine
plan: 04
subsystem: testing
tags: [temporal-testing, junit5, mockito, workflow-test, activity-test, deviation-detection]

# Dependency graph
requires:
  - phase: 02-pathway-engine plan 02
    provides: PatientPathwayWorkflowImpl, PatientPathwayWorkflow interface, PathwayEvaluationActivity interface
  - phase: 02-pathway-engine plan 03
    provides: PathwayEvaluationActivityImpl, AlertGenerationActivityImpl

provides:
  - PatientPathwayWorkflowTest: 6 workflow unit tests with TestWorkflowExtension and time-skipping
  - PathwayEvaluationActivityTest: 8 activity unit tests proving all deviation types, override, dedup, and alert closure
  - AlertGenerationActivityTest: 3 activity unit tests proving standalone alert creation with dedup
  - temporal-testing 1.32.0 as test-scope dependency in pom.xml
  - TemporalConfig fix: proxyBeanMethods=false resolves CGLIB compatibility issue in integration tests

affects:
  - 03-xx (test patterns established for Phase 3 REST controller tests)

# Tech tracking
tech-stack:
  added:
    - "temporal-testing 1.32.0 (test scope) — Temporal's in-process test server with time skipping"
  patterns:
    - "TestWorkflowExtension with setDoNotStart(true): register activity stubs before testEnv.start(), use testEnv.sleep(Duration) for virtual time skipping"
    - "Concrete inner stub classes for Temporal activity mocking — Mockito dynamic proxies fail Temporal's @ActivityMethod reflection scanner; AtomicInteger counters replace Mockito.verify()"
    - "Plain JUnit 5 + Mockito for activity unit tests — no Spring context needed when all repositories are mocked; ObjectMapper constructed directly"
    - "@Configuration(proxyBeanMethods=false) on configuration classes with private constructors or constants-only design — prevents Spring CGLIB enhancement failure"

key-files:
  created:
    - src/test/java/com/onconavigator/workflow/PatientPathwayWorkflowTest.java
    - src/test/java/com/onconavigator/activity/PathwayEvaluationActivityTest.java
    - src/test/java/com/onconavigator/activity/AlertGenerationActivityTest.java
  modified:
    - pom.xml
    - src/main/java/com/onconavigator/config/TemporalConfig.java

key-decisions:
  - "Concrete stub activity classes instead of Mockito proxies for Temporal worker registration: Mockito's subclass-based dynamic proxies inherit @ActivityMethod annotations on overriding methods, which Temporal's reflection scanner rejects with 'This annotation can be used only on the interface method it implements'. Concrete inner classes implementing the interface are the safe alternative."
  - "AtomicInteger invocation counters in stub classes replace Mockito.verify() for Temporal activity tests: Since we use concrete stubs not Mockito mocks, call counting is done explicitly via AtomicInteger fields. This is cleaner and avoids the proxy issue entirely."
  - "@Configuration(proxyBeanMethods=false) added to TemporalConfig: Spring CGLIB cannot proxy a @Configuration class with a private constructor. Since TemporalConfig only holds constants and has no @Bean methods, proxyBeanMethods=false is correct and safe."

patterns-established:
  - "Temporal workflow testing: TestWorkflowExtension + concrete stub activities + testEnv.sleep() for time skipping"
  - "Activity unit testing: plain JUnit5 + Mockito-mocked repositories + direct instantiation of activity impl"

requirements-completed: [INFR-03, INFR-04, PATH-03, PATH-04, PATH-05, PATH-06, PATH-07, PATH-08]

# Metrics
duration: 9min
completed: 2026-04-30
---

# Phase 02 Plan 04: Workflow and Activity Tests Summary

**17 unit tests proving the pathway engine core value: timer+signal loop (INFR-03/04), all three deviation types (PATH-03/04/05), physician override suppression (PATH-08), alert deduplication (PATH-06), graceful deactivation (D-08), and natural completion (D-09) — all passing alongside existing Phase 1 tests in 40-test suite BUILD SUCCESS**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-04-30T14:45:23Z
- **Completed:** 2026-04-30T14:54:xx Z
- **Tasks:** 2 of 2
- **Files created:** 3, modified: 2

## Accomplishments

- `temporal-testing 1.32.0` added to pom.xml as test-scope dependency — provides `TestWorkflowExtension` with in-process Temporal server and virtual time skipping
- `PatientPathwayWorkflowTest` (6 tests): proves the signal+timer loop works correctly using time-skipping; testWorkflowStartsAndCallsEvaluate fires after 25h virtual time; testSignalWakesWorkflowEarly wakes the workflow before the 24h timer; testDeactivationSignalTerminatesWorkflow verifies closeOpenAlerts() is called on D-08 path; testPathwayCompletionExitsWorkflow proves D-09 natural exit; testQueryMethodReturnsMonitoring confirms the @QueryMethod returns "MONITORING" while active; testMultipleSignalsHandled proves 3 rapid signals coalesce to at least 1 evaluate() call
- `PathwayEvaluationActivityTest` (8 tests): fully exercises deviation detection without a database — mocked repositories + real ObjectMapper; proves MISSING_EVENT (PATH-03), DELAYED_EVENT (PATH-04), OUT_OF_ORDER (PATH-05) each create the correct AlertType; proves physician override prevents alert creation (PATH-08); proves duplicate dedup prevents second OPEN alert (PATH-06); proves allStepsComplete=true when all steps COMPLETED (D-09); proves evaluate() returns non-null PathwayEvaluationResult (PATH-07); proves closeOpenAlerts() sets RESOLVED status and calls saveAll() (D-08)
- `AlertGenerationActivityTest` (3 tests): proves standalone alert creation with correct fields, duplicate suppression returning early (PATH-06), and correct AlertType mapping from String parameter
- **Auto-fix (Rule 1 - Bug)**: Fixed `TemporalConfig.@Configuration(proxyBeanMethods=false)` — `FullStackIntegrationTest` was failing in the full suite because Spring CGLIB could not proxy a `@Configuration` class with a private constructor; this was a pre-existing bug from Plan 02-02 discovered when running `./mvnw test`
- Full test suite: **40 tests, 0 failures, 0 errors** — all Phase 1 and Phase 2 tests pass together

## Task Commits

1. **Task 1: temporal-testing dependency and workflow unit tests** - `be2d6a4` (test)
2. **Task 2: Activity unit tests for deviation detection, override, and dedup** - `b96f131` (test, includes TemporalConfig fix)

**Plan metadata:** _(final docs commit hash recorded below after state updates)_

## Files Created/Modified

- `pom.xml` — Added `io.temporal:temporal-testing:1.32.0` as `<scope>test</scope>` dependency
- `src/test/java/com/onconavigator/workflow/PatientPathwayWorkflowTest.java` — 6 workflow unit tests with TestWorkflowExtension, time skipping, and concrete stub activities
- `src/test/java/com/onconavigator/activity/PathwayEvaluationActivityTest.java` — 8 activity unit tests: all 3 deviation types, override suppression, dedup, completion check, logging, and alert closure
- `src/test/java/com/onconavigator/activity/AlertGenerationActivityTest.java` — 3 activity unit tests: alert creation, duplicate suppression, alert type correctness
- `src/main/java/com/onconavigator/config/TemporalConfig.java` — Added `proxyBeanMethods = false` to `@Configuration` to fix CGLIB incompatibility with private constructor

## Decisions Made

- Used **concrete inner stub classes** instead of Mockito dynamic proxies for Temporal activity registration. Mockito creates a subclass of the activity interface and overrides all methods; the overriding methods inherit the `@ActivityMethod` annotation from the interface. Temporal's `POJOActivityImplMetadata` rejects this with: "Found @ActivityMethod annotation on [MockitoMock method] — This annotation can be used only on the interface method it implements." The fix is concrete classes implementing the interface directly.
- Used **AtomicInteger counters** in stub classes instead of `Mockito.verify()` for counting invocations. Since the stubs are not Mockito mocks, invocation counting must be explicit. AtomicInteger is thread-safe and works correctly with Temporal's worker thread model.
- Applied `@Configuration(proxyBeanMethods = false)` to `TemporalConfig`. The class has a private constructor by design (constants-only utility class). CGLIB proxy requires a visible constructor. Since `TemporalConfig` has no `@Bean` methods, `proxyBeanMethods = false` is semantically correct and has no functional impact.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed TemporalConfig CGLIB incompatibility**
- **Found during:** Task 2 when running `./mvnw test` (full suite verification)
- **Issue:** `FullStackIntegrationTest` was failing with `BeanDefinitionStoreException: Could not enhance configuration class [TemporalConfig]. No visible constructors in class TemporalConfig`. Spring CGLIB enhancement (required for `@Configuration` classes) cannot proxy classes with only a private constructor.
- **Fix:** Added `proxyBeanMethods = false` to `@Configuration` on `TemporalConfig`. Since the class only declares `static final` constants and has no `@Bean` methods, proxy bean methods are not needed.
- **Files modified:** `src/main/java/com/onconavigator/config/TemporalConfig.java`
- **Commit:** `b96f131`

**2. [Rule 1 - Bug] Replaced Mockito proxy activity mocks with concrete stub classes**
- **Found during:** Task 1 execution
- **Issue:** Temporal's `POJOActivityImplMetadata` reflection scanner rejected Mockito-generated proxy classes when registering them as activity implementations. The error was: "Found @ActivityMethod annotation on [MockitoMock method] — This annotation can be used only on the interface method it implements."
- **Fix:** Created `LoopingEvaluationActivity` and `CompletingEvaluationActivity` inner classes implementing `PathwayEvaluationActivity` directly, using `AtomicInteger` counters for invocation tracking.
- **Files modified:** `PatientPathwayWorkflowTest.java` (rewritten)
- **Commit:** `be2d6a4`

## Known Stubs

None. All tests are fully wired — no placeholder data, TODO stubs, or hardcoded empty values.

## Threat Flags

No new security-relevant surface introduced. All test code:
- Uses synthetic UUIDs (no real PHI)
- Creates no new network endpoints
- Adds no authentication paths
- Uses mocked repositories (no DB access in unit tests)

## Self-Check: PASSED
