# Phase 4: AI Document Ingestion & Alert Enhancement - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md -- this log preserves the alternatives considered.

**Date:** 2026-05-01
**Phase:** 04-ai-document-ingestion
**Areas discussed:** PDF text extraction, Patient matching flow, Drag-and-drop UX, Claude prompt design

---

## PDF Text Extraction

### What types of clinical PDFs will the system handle?

| Option | Description | Selected |
|--------|-------------|----------|
| Digital only | Only electronically-generated PDFs. No OCR needed -- use Apache PDFBox for direct text extraction. | |
| Digital + scanned/faxed | Support both text-selectable PDFs AND scanned/faxed images. Requires OCR. | :heavy_check_mark: |
| Let Claude decide | Claude picks the best extraction approach during planning | |

**User's choice:** Digital + scanned/faxed
**Notes:** Two-path extraction pipeline needed: direct text extraction for digital PDFs, OCR for scanned documents.

### Which OCR approach for scanned/faxed PDFs?

| Option | Description | Selected |
|--------|-------------|----------|
| Tesseract (local) | Open-source OCR running locally in Docker. No external API calls, no PHI leaves system. | |
| Claude vision | Send PDF page as image to Claude vision API. Superior quality. Requires BAA. | |
| Hybrid: Tesseract first, Claude vision fallback | Try Tesseract locally. If confidence is low, escalate to Claude vision. | :heavy_check_mark: |

**User's choice:** Hybrid approach
**Notes:** Minimizes API calls while maintaining quality on difficult faxes. BAA still required for the fallback path.

### Failure threshold for manual entry fallback

| Option | Description | Selected |
|--------|-------------|----------|
| Confidence score | Tesseract confidence below threshold triggers escalation. | |
| Content length check | Extracted text shorter than minimum considered failed. | |
| Let Claude decide | Claude picks the best failure detection heuristic | :heavy_check_mark: |

**User's choice:** Let Claude decide
**Notes:** Deferred to Claude's discretion during planning.

### Accepted file types

| Option | Description | Selected |
|--------|-------------|----------|
| PDF only | Simpler pipeline. Clinical documents almost always PDFs. | |
| PDF + images (JPEG, PNG) | Accept photographed documents too. Adds image conversion step. | :heavy_check_mark: |
| Let Claude decide | Claude picks based on test corpus needs | |

**User's choice:** PDF + images (JPEG, PNG)
**Notes:** Covers staff who photograph faxes with phones rather than scanning.

---

## Patient Matching Flow

### How should the system match a document to a patient?

| Option | Description | Selected |
|--------|-------------|----------|
| MRN extraction first | Claude extracts MRN, system looks up via HMAC token. Manual search if no match. | |
| Multi-field extraction | Extract MRN, name, DOB. Try MRN first, fall back to name+DOB fuzzy match. | :heavy_check_mark: |
| Always manual selection | After classification, always present patient search. | |

**User's choice:** Multi-field extraction
**Notes:** Handles documents without MRN by falling back to name+DOB matching.

### Ambiguous or multiple patient matches

| Option | Description | Selected |
|--------|-------------|----------|
| Show ranked candidates | Display possible matches with confidence. User picks or searches manually. | :heavy_check_mark: |
| Best match with confirm | Auto-select highest-confidence match, require explicit confirmation. | |
| Let Claude decide | Claude picks the best UX pattern | |

**User's choice:** Show ranked candidates
**Notes:** Always requires user confirmation before proceeding.

### No patient match found

| Option | Description | Selected |
|--------|-------------|----------|
| Offer new patient creation | Pre-fill Phase 3 patient wizard with extracted demographics. | :heavy_check_mark: |
| Manual search only | Show search box. If not found, navigate to patient creation separately. | |
| Let Claude decide | Claude picks based on UX research | |

**User's choice:** Offer new patient creation
**Notes:** Document ingestion can serve as a patient onboarding path.

### Name+DOB matching against encrypted PHI

| Option | Description | Selected |
|--------|-------------|----------|
| In-memory decrypt | Same as Phase 3 name search. Acceptable at pilot scale <500 patients. | :heavy_check_mark: |
| Add HMAC tokens for name+DOB | Deterministic HMAC columns for DB-level matching. More work, scales better. | |
| Let Claude decide | Claude picks based on scale analysis | |

**User's choice:** In-memory decrypt
**Notes:** Consistent with Phase 3 approach. Scale optimization deferred.

---

## Drag-and-Drop UX

### Where should the drop zone live?

| Option | Description | Selected |
|--------|-------------|----------|
| Dashboard + patient detail | Two entry points mirroring Phase 3 care event pattern. | :heavy_check_mark: |
| Dedicated upload page | Separate 'Documents' page in sidebar nav. | |
| Dashboard only | Single drop zone on dashboard. | |
| Let Claude decide | Claude picks optimal placement | |

**User's choice:** Dashboard + patient detail
**Notes:** Mirrors Phase 3 two-entry-point pattern. Patient detail drop zone pre-selects patient.

### Processing progress display

| Option | Description | Selected |
|--------|-------------|----------|
| Inline stepper | Modal with steps: Uploading -> Extracting -> Classifying -> Matching -> Ready. | :heavy_check_mark: |
| Background with toast | File uploads in background. Toast notification on completion. | |
| Let Claude decide | Claude picks best progress pattern | |

**User's choice:** Inline stepper
**Notes:** Clinical staff need to see and trust the automation process.

### Pre-filled care event wizard approach

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse existing forms | Same Phase 3 form pre-filled with extracted data. Add document classification section. | :heavy_check_mark: |
| New document wizard | Specialized multi-step wizard with classification review, match confirmation, then form. | |
| Let Claude decide | Claude picks based on existing code patterns | |

**User's choice:** Reuse existing forms
**Notes:** Minimal new UI code. Keeps UX consistent with Phase 3.

### PDF viewing after save

| Option | Description | Selected |
|--------|-------------|----------|
| Inline PDF viewer | "View Document" button opens PDF in modal using browser native renderer. Download also available. | :heavy_check_mark: |
| Download only | "Download PDF" link. No inline viewing. | |
| Let Claude decide | Claude picks based on feasibility | |

**User's choice:** Inline PDF viewer
**Notes:** Users can reference the source document without leaving the app.

---

## Claude Prompt Design

### Information sent for document classification

| Option | Description | Selected |
|--------|-------------|----------|
| Full extracted text | Send full document text to Claude. Contains PHI. Requires Anthropic BAA. | :heavy_check_mark: |
| Redacted text | Strip likely PHI before sending. Reduces exposure but risks removing useful context. | |
| Structure-only features | Send structural features only (headers, keywords). Zero PHI risk, lower accuracy. | |

**User's choice:** Full extracted text
**Notes:** Locks in Anthropic BAA as hard prerequisite. Classification and field extraction in single call.

### Non-standard alert definition

| Option | Description | Selected |
|--------|-------------|----------|
| Catch-all for unmatched templates | Claude generates text when deviation doesn't match any template pattern. Template stays primary. | :heavy_check_mark: |
| All alerts get Claude enhancement | Every alert goes through Claude. Template text is fallback. Higher API usage. | |
| Let Claude decide | Claude determines boundary during planning | |

**User's choice:** Catch-all for unmatched templates
**Notes:** Existing template flow stays primary. Claude is enhancement for edge cases.

### PHI boundary for alert generation

| Option | Description | Selected |
|--------|-------------|----------|
| Anonymized clinical context | Cancer type, step name, deviation type, time windows. NO patient identifiers. | :heavy_check_mark: |
| Template variables only | Only deviation_type, step_name, window_days, elapsed_days. Minimal context. | |
| Full patient context with BAA | Send patient-specific context since BAA already needed for documents. | |

**User's choice:** Anonymized clinical context
**Notes:** Two distinct PHI boundaries: document classification (full text, BAA-covered) vs alert generation (zero-PHI, anonymized only).

### Circuit breaker pattern

| Option | Description | Selected |
|--------|-------------|----------|
| Resilience4j circuit breaker | Standard closed -> open -> half-open pattern. Structured fallbacks per use case. | :heavy_check_mark: |
| Simple retry + fallback | Retry once, then fall back immediately. No state tracking. | |
| Let Claude decide | Claude picks resilience pattern | |

**User's choice:** Resilience4j circuit breaker
**Notes:** Document classification fallback: manual dropdown. Alert generation fallback: template text. All fallbacks logged.

---

## Claude's Discretion

- Extraction failure detection heuristic (confidence score vs content length vs hybrid)
- Tesseract confidence threshold for Claude vision escalation
- Exact Resilience4j parameters (failure count, timeout, half-open retry)
- PDF preview thumbnail generation
- Image conversion pipeline details
- Claude prompt templates and response schema design
- Test corpus composition (count per type, de-identification approach)
- Spring AI ChatClient config and model selection
- Document entity schema design

## Deferred Ideas

None -- discussion stayed within phase scope
