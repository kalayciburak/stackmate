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
