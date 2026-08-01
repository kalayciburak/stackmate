# StackMate Phase 4 progress

This log records Phase 4 without altering historical Phase 1-3 reports.

## Phase 0 - Baseline reproduction

Status: completed on 2026-08-02

### Completed work

- Verified clean Phase 3 HEAD and created the local `phase-3-complete` annotated tag.
- Created `phase-4-english-small-model-tournament`; no remote is configured and no
  push occurred.
- Read the current product, benchmark, model, privacy, progress, and ADR decisions.
- Inspected all Maven modules and their direct dependency boundaries.
- Rebuilt all 11 reactor projects with Java 21.
- Re-ran packaged English/Turkish, disabled, duplicate, redaction, and fake-worker
  E2E coverage through Failsafe.
- Reproduced the Phase 3 Qwen3.5-2B English greedy screen with the existing ignored
  artifacts.

### Results

- Surefire: 314; Failsafe: 12; failures/errors/skips: 0/0/0.
- Existing 2B reference: 10/10 valid, cause, and check selection.
- No worker remained after verification or real-model execution.
- No production code changed during Phase 0.

### Next gate

Inventory every runtime localization surface, freeze its classification, then remove
Turkish runtime behavior while preserving complete Turkish documentation and all
historical evidence.

## Gate A - English-only runtime product

Status: completed on 2026-08-02

### Completed work

- Removed the runtime language configuration, public language enums, Turkish prompt
  resources, Turkish renderer dictionaries, Turkish sample profile, and model language
  fields.
- Kept Turkish privacy detection for national identity-like values and arbitrary Unicode
  protocol coverage because neither is a localization surface.
- Made unknown `stackmate` properties fail binding so stale `stackmate.language` usage is
  reported instead of being silently ignored.
- Added a fixed English-output validator and migration notes for the breaking 0.1.0
  snapshot API cleanup.
- Preserved the full Turkish README as Turkish documentation for the English-only runtime.

### Verification

- `./mvnw.cmd --batch-mode --no-transfer-progress -DskipTests spotless:check`: PASS across
  all 11 reactor projects; no formatter apply command was used.
- `./mvnw.cmd --batch-mode --no-transfer-progress clean verify`: PASS in 57.528 seconds.
- Surefire: 305; Failsafe: 11; failures/errors/skips: 0/0/0.
- Maven Enforcer Java, Maven, dependency convergence, and banned-dependency rules passed.
- Core JaCoCo gates passed.
- Built inference API, JVM inference, auto-configuration, and sample JARs contain no
  `RenderLanguage`, `InferenceLanguage`, `/tr/` prompt resource, or Turkish sample profile.
- Generated configuration metadata contains no `stackmate.language` property.
- No inference worker remained after verification.

### Decision

Gate A passed. The runtime product and model contract are English-only; Turkish remains a
complete documentation surface. Candidate research and the frozen V3 screen follow.

## Gate B - Candidate research and corpus freeze

Status: in progress on 2026-08-02

### Completed work

- Froze and committed the independent 20-case V3 screening corpus before model
  execution; 10 cases are development, 10 are holdout, and 16 are real runtime or
  integration captures.
- Recorded manifest SHA-256
  `e8f9fa194f0b53c78a35e2821ed6358195b0d535c86e82e4fc50a9b8f0085a78`.
- Verified total parameters from pinned official repository metadata rather than
  model names.
- Rejected Qwen3-1.7B before download because its 2,031,739,904 parameters exceed the
  hard cap.
- Classified the mandatory Qwen3.5-2B reference as an ineligible historical control
  because its multimodal total is 2,274,069,824.
- Selected three eligible candidates: SmolLM2-360M-Instruct, Qwen3-0.6B, and
  Qwen2.5-1.5B-Instruct.
- Pinned official llama.cpp release `b10221` and the exact Windows archive digest for
  isolated screening.

### Next step

Check disk capacity, download and verify only the three new pinned Q4_K_M research
artifacts and the pinned runtime, then execute the frozen screen in ascending model
size order.

### Artifact and harness verification

- Confirmed 549.74 GiB free before downloading 1.76 GiB of new artifacts.
- Verified and activated all three eligible Q4_K_M artifacts by exact size and
  SHA-256 under ignored `.stackmate/phase4/models/` paths.
- Verified the official b10221 Windows archive and executed
  `llama-completion 10221 (815a2a591)` built with Clang 20.1.8.
- Added a strict candidate manifest and parameter-cap validation. Its SHA-256 is
  `a79c5f359b4bcb05f927eb8921f5fc32109db9abc6c3b9b706a47590ad0f68d2`.
- Updated the isolated lab to consume the frozen 20-case V3 corpus, use per-request
  JSON schema plus Phase 2 semantic validation, preserve one bounded repair, detect
  safety failures across both attempts, and emit manual-review material.
- Added a validator regression for non-Latin output after a real COMPACT result
  included Chinese characters in otherwise English prose.

### Verification so far

- Candidate/corpus manifest tests: 6 passed.
- Inference output-validator tests: 12 passed.
- Candidate MICRO smoke: one real-model case passed schema, selection, and validator
  checks.
- Spotless check: passed for all affected reactor modules without applying a
  formatter.
