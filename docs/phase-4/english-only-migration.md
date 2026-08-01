# English-only runtime migration

Phase 4 changes StackMate-generated runtime output to English only. Turkish documentation remains available in
`README.tr.md`; historical Phase 1-3 reports retain their original bilingual measurements.

## Required user change

Remove this property from YAML, properties files, environment mappings, and tests:

```yaml
stackmate:
  language: tr
```

There is no replacement. `stackmate.language=en` should also be removed. StackMate configuration now rejects
unknown fields, so a stale language setting fails startup instead of appearing to work.

## Pre-release API changes

The project has not been released. The following public language-specific types and components were removed:

- `io.github.kalayciburak.stackmate.output.RenderLanguage`
- `io.github.kalayciburak.stackmate.inference.InferenceLanguage`
- `StackMateProperties.Language` and `getLanguage`/`setLanguage`
- the language components of `InferenceRequest`, `StructuredInferenceOutput`, and `ValidatedInferenceOutput`
- the `english`/`turkish` flags and language selector on `ModelCapabilities`

`RenderOptions`, `InferenceRequest`, `StructuredInferenceOutput`, `ModelCapabilities`, and
`DeterministicInferenceRequestFactory#create` therefore have shorter constructors/signatures. Recompile custom
extensions against the Phase 4 API.

## Inventory classification

| Classification | Phase 3 surface | Phase 4 treatment |
|---|---|---|
| RUNTIME_SUPPORT | language property/enums, Turkish renderer maps, Turkish prompts, bilingual local renderer, model language flags | Removed or reduced to fixed English |
| TEST_SUPPORT | Turkish renderer, prompt, property, inference and packaged-process scenarios | Tests solely for localization removed; English safety tests retained |
| BENCHMARK_SUPPORT | `stackmate.benchmark.language`, bilingual llama.cpp loop, Turkish manual-review branch | Removed; Phase 4 tournament is English only |
| HISTORICAL_DOCUMENTATION | Phase 1-3 progress, benchmarks, reviews, galleries and ADRs | Preserved byte-for-byte |
| CURRENT_DOCUMENTATION | READMEs, changelog, troubleshooting, model selection and threat model | Updated to describe English runtime and Turkish documentation |
| UNRELATED_TEXT | Turkish national identity-number sanitization and arbitrary Unicode protocol fixtures | Retained as privacy/protocol coverage |
