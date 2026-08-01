# Phase 2 inference schema V2 migration

This migration affects only applications that directly implemented StackMate's experimental inference SPI or built
custom fake engines against `0.1.0-SNAPSHOT`. Deterministic-starter users do not need to change code.

## Request changes

- Supply `schemaVersion` and `promptVersion`, both `"2"`.
- Replace free-form fact text with `InferenceFact(id, type, value)`.
- Replace `causes` and `checks` with deterministic `candidateCauses` and `candidateChecks`; each candidate contains only
  its ID, trusted text, and supporting evidence IDs.
- Replace `sourceLocations` with typed `APPLICATION_LOCATION` facts.
- Supply the trusted `InferenceIncident` category and evidence strength as input only.
- Optionally supply bounded terminology definitions.

## Output changes

Return exactly these fields: `schemaVersion`, `language`, `summary`, `explanation`, `selectedCauseIds`,
`selectedCheckIds`, `usedEvidenceIds`, and `limitations`.

Do not return generated cause/check objects, category, root type, confidence, or source locations. Remove uses of
`StructuredCause`, `StructuredCheck`, and `SourceLocation`; these types were deleted rather than deprecated because
their authority model is unsafe. Selected IDs are resolved to trusted deterministic candidates only after validation.

For protocol tests that intentionally exercise inference regardless of evidence strength, configure:

```yaml
stackmate:
  inference:
    trigger-policy: always
```
