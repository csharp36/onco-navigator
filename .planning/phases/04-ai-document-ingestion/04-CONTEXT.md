# Phase 4: AI Document Ingestion & Alert Enhancement - Context

**Gathered:** 2026-05-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Clinical documents (PDFs and images) can be dragged into the dashboard, classified by Claude AI, matched to patients, and used to pre-fill care event recording — reducing manual data entry. Additionally, non-standard deviation alerts that don't match pathway template text get Claude-generated plain-language descriptions with circuit breaker fallback to template text.

This phase delivers: a test corpus of synthetic clinical PDFs, PDF/image text extraction pipeline (PDFBox + Tesseract OCR + Claude vision fallback), Claude-powered document classification, multi-field patient matching (MRN + name + DOB), pre-filled care event wizard from extracted data, PDF/image blob storage in PostgreSQL linked to care events, inline PDF viewer, Claude-generated alert descriptions for non-standard deviations, and Resilience4j circuit breaker for all Claude API calls.

This phase does NOT build SMS notifications (V2), EMR integration (V2), pathway template admin UI (V2 ADV-01), or predictive risk scoring (V2 ADV-03).

</domain>

<decisions>
## Implementation Decisions

### PDF Text Extraction Pipeline
- **D-01:** Support both **digitally-generated PDFs AND scanned/faxed documents**. Two-path extraction: Apache PDFBox for text-selectable PDFs, Tesseract OCR for scanned/image-based documents.
- **D-02:** **Hybrid OCR approach** — Try Tesseract locally first. If extraction quality is low (confidence below threshold or insufficient content), escalate to Claude vision API as fallback. Minimizes API calls while maintaining quality on difficult faxes.
- **D-03:** Accept **PDF + image files (JPEG, PNG)** — not just PDFs. Photographed documents are converted to a processable format before extraction. Covers staff who photograph faxes with phones.
- **D-04:** When both Tesseract and Claude vision fail to extract usable text, open the care event wizard with **blank fields for manual entry** while still attaching the source file. The failure detection heuristic (confidence score, content length, or hybrid) is at Claude's discretion.

### Patient Matching
- **D-05:** **Multi-field extraction** from document text — extract MRN, patient name, and DOB. Try MRN match first via existing HMAC token lookup (Phase 3 D-04). Fall back to name+DOB matching if MRN not found or doesn't match.
- **D-06:** When match is uncertain or multiple patients match, **show ranked candidates** with confidence indicators (high/medium/low). User picks the correct patient or searches manually. User confirmation is always required before proceeding.
- **D-07:** When no patient match is found, **offer new patient creation** — pre-fill the existing Phase 3 patient creation wizard with any extracted demographics (name, DOB, MRN). User confirms/corrects before saving.
- **D-08:** Name+DOB matching uses **in-memory decryption** — load patient records, decrypt PHI fields, compare in memory. Same approach as Phase 3 name search. Acceptable at pilot scale (<500 patients).

### Drag-and-Drop UX
- **D-09:** Drop zones on **both the dashboard AND patient detail page** — mirrors the Phase 3 two-entry-point pattern for care events. Dashboard drop zone goes through full classification + matching flow. Patient detail drop zone pre-selects that patient, skipping the matching step.
- **D-10:** Processing progress shown via an **inline stepper** modal/panel: Uploading -> Extracting text -> Classifying -> Matching patient -> Ready. Each step shows spinner then checkmark. On completion, transitions to the pre-filled care event form.
- **D-11:** Pre-filled care event wizard **reuses existing Phase 3 care event forms** — same form (event type, date, status, notes) pre-filled with extracted data. Add a read-only section showing the source document classification and a PDF preview thumbnail. Minimal new UI code.
- **D-12:** After care event is saved, **inline PDF viewer** available on the care event detail or patient detail page. "View Document" button opens the PDF in a modal/panel using the browser's native PDF renderer. Download button also available.

### Claude Prompt Design
- **D-13:** Document classification sends **full extracted text** to Claude — clinical documents inherently contain PHI. This requires an **Anthropic BAA as a hard prerequisite**. Classification and patient field extraction (MRN, name, DOB, event type, event date) happen in a single Claude API call.
- **D-14:** Alert generation maintains the Phase 2 **zero-PHI boundary** — sends only anonymized clinical context: cancer type, pathway step name, deviation type, time window details, which steps are complete/missing. NO patient name, MRN, DOB, or identifiers. Two distinct PHI boundaries: document classification (full text, BAA-covered) vs alert generation (zero-PHI).
- **D-15:** Non-standard alert generation is a **catch-all for unmatched templates** — pathway templates define alert text for known deviations (existing Phase 2 behavior). When the deviation doesn't match any template pattern (unusual timing, edge-case step combinations), Claude generates the description. Template flow stays primary, Claude is enhancement.
- **D-16:** **Resilience4j circuit breaker** for all Claude API calls — closed -> open after N failures (e.g., 3 in 30s) -> half-open after timeout. Document classification fallback: manual classification dropdown. Alert generation fallback: template text. All fallbacks logged for monitoring.

### Claude's Discretion
- Extraction failure detection heuristic (confidence score vs content length vs hybrid)
- Tesseract confidence threshold for Claude vision escalation
- Exact Resilience4j circuit breaker parameters (failure count, timeout window, half-open retry count)
- PDF preview thumbnail generation approach
- Image-to-processable-format conversion pipeline details
- Claude prompt template structure and response schema design
- Test corpus composition (number of documents per type, de-identification approach)
- Spring AI ChatClient configuration and model selection (claude-3-5-sonnet vs claude-3-7-sonnet)
- Document entity schema design (bytea storage, metadata columns)

</decisions>

<specifics>
## Specific Ideas

- Document classification and field extraction should happen in a single Claude call to minimize latency and API cost
- The inline stepper UX during processing gives the user confidence the system is working (clinical staff need to trust automation)
- Two distinct PHI boundaries: document processing is BAA-covered (full text), alert generation stays zero-PHI (anonymized context only). This is a deliberate security architecture decision, not a compromise.
- Reusing Phase 3 care event forms for pre-filled wizard keeps the UI consistent and reduces new code
- The "Offer new patient creation" on no-match means document ingestion can serve as a patient onboarding path, not just an event recording shortcut
- STATE.md notes that Anthropic BAA negotiation should have started during Phase 1-2. If BAA is not in place, document classification cannot go live — but the code can still be built and tested against synthetic data.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Clinical Document Types & Pathway Definitions
- `docs/Onco-Navigator AI - V1 Feature Specification v2.docx` -- Contains pathway definitions, document types referenced in pathway steps (pathology report, radiology report, etc.), and example alert scenarios. Convert to text with `textutil -convert txt` before reading.

### Requirements
- `.planning/REQUIREMENTS.md` -- AI-01 through AI-04 (AI integration), DOC-01 through DOC-05 (document ingestion)

### Prior Phase Context
- `.planning/phases/02-pathway-engine/02-CONTEXT.md` -- Decisions D-05 (dual monitoring), D-10 (template text), D-11 (physician override). Alert generation activity must integrate with the existing template-first approach.
- `.planning/phases/03-working-application/03-CONTEXT.md` -- Decisions D-01 through D-04 (data entry patterns), D-09 (patient detail layout). Document ingestion UX must be consistent with established patterns.

### Existing Backend Code
- `src/main/java/com/onconavigator/activity/AlertGenerationActivityImpl.java` -- Current alert creation with template text. Phase 4 adds Claude-generated text branch for non-standard deviations.
- `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` -- Creates alerts directly during evaluation. Phase 4 modifies to detect non-standard deviations and route to Claude.
- `src/main/java/com/onconavigator/domain/CareEvent.java` -- Care event entity. Phase 4 adds document attachment linkage.
- `src/main/java/com/onconavigator/domain/Alert.java` -- Alert entity with `deviationDescription` and `suggestedAction` fields that Claude generates for non-standard cases.
- `src/main/java/com/onconavigator/domain/enums/CareEventType.java` -- PATHOLOGY_REPORT, IMAGING, LAB_WORK, etc. map to document types.
- `src/main/java/com/onconavigator/security/HmacTokenService.java` -- HMAC token generation for MRN search. Reuse for document-to-patient MRN matching.
- `src/main/java/com/onconavigator/web/CareEventController.java` -- Existing care event endpoints. Phase 4 adds document upload endpoint.
- `src/main/java/com/onconavigator/service/PatientService.java` -- Patient lookup. Phase 4 uses for multi-field matching.

### Existing Frontend Code
- `frontend/src/features/patients/QuickAddCareEventDialog.tsx` -- Existing care event form. Phase 4 reuses for pre-filled wizard.
- `frontend/src/routes/index.tsx` -- Dashboard page. Phase 4 adds drop zone.
- `frontend/src/routes/patients/$patientId.tsx` -- Patient detail page. Phase 4 adds drop zone.
- `frontend/src/lib/api-client.ts` -- API client. Needs multipart/form-data support for file upload.

### Technology
- Spring AI 1.1.0 (`spring-ai-anthropic-spring-boot-starter`) -- Claude API integration via ChatClient abstraction (CLAUDE.md tech stack)
- Resilience4j -- Circuit breaker for Claude API calls
- Apache PDFBox -- PDF text extraction for digital documents
- Tesseract (via Tess4J or similar) -- OCR for scanned documents

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AlertGenerationActivityImpl` -- Standalone alert creation with dedup. Phase 4 adds Claude-generated text path.
- `HmacTokenService` -- HMAC token for MRN lookup. Reuse for document-to-patient matching.
- `QuickAddCareEventDialog` -- Existing care event form. Reuse for pre-filled document-based event creation.
- `api-client.ts` -- Generic fetch wrapper. Extend for multipart file upload.
- `EncryptionConverter` -- JPA converter for PHI fields. Apply to any new PHI-bearing columns.
- `Card`, `Dialog`, `Badge`, `Input` shadcn components -- For document processing modal, classification display.

### Established Patterns
- Two entry points for same operation (quick-add + full form) -- Phase 3 D-03. Apply to document drop zones.
- AES-GCM encryption on PHI fields via `@Convert` annotation
- Hibernate Envers `@Audited` on all ePHI entities
- PostgreSQL ENUM types mapped to Java enums
- TanStack Query for server state, TanStack Router for routing
- Tailwind v4 with `@theme` in app.css

### Integration Points
- Dashboard page (`routes/index.tsx`) -- Add drop zone component
- Patient detail page (`routes/patients/$patientId.tsx`) -- Add drop zone component
- `CareEventController` -- Add document upload endpoint (multipart)
- `PathwayEvaluationActivityImpl` -- Modify to detect non-standard deviations and route to Claude
- `AlertGenerationActivityImpl` -- Add Claude text generation branch
- `application-local.yml` -- Add Spring AI / Anthropic configuration properties
- `pom.xml` -- Add spring-ai-anthropic-spring-boot-starter, Resilience4j, PDFBox, Tess4J dependencies

</code_context>

<deferred>
## Deferred Ideas

None -- discussion stayed within phase scope

</deferred>

---

*Phase: 04-ai-document-ingestion*
*Context gathered: 2026-05-01*
