# ADR 0017: English-only runtime product

- Status: Accepted
- Date: 2026-08-02

## Context

StackMate is still an unreleased `0.1.0-SNAPSHOT`. Its Turkish runtime renderer, prompt pair, language configuration,
and language-bearing inference records doubled product and validation paths while the Phase 2 and Phase 3 model
experiments did not establish useful Turkish inference. Phase 4 evaluates whether a model no larger than 2B can
perform one narrow explanation task; retaining two runtime languages would make that result ambiguous.

ADR numbers 0015 and 0016 were already assigned in Phase 3, so this decision uses the next available number instead
of overwriting history.

## Decision

StackMate-generated runtime text is English only. Original exception messages and other evidence may contain any
language; StackMate sanitizes them and does not translate them.

Remove `stackmate.language`, `StackMateProperties.Language`, `RenderLanguage`, `InferenceLanguage`, Turkish prompt
resources, the Turkish sample profile, Turkish runtime tests, and Turkish benchmark modes. The inference request and
output contracts no longer carry a language field. Output validation applies one fixed English policy.

Keep Turkish identity-number redaction because it is a privacy control, not localization. Keep `README.tr.md` as a
complete Turkish guide and preserve all Phase 1-3 reports unchanged as historical evidence.

Unknown StackMate configuration is rejected, so a stale `stackmate.language` setting fails clearly instead of being
silently ignored.

## Consequences

- Runtime rendering, prompts, validation, samples, and benchmarks have one product path.
- The public pre-release inference and rendering APIs have breaking constructor changes.
- Users must remove `stackmate.language`; there is no replacement property.
- Adding another runtime language later requires a new product decision and evidence, not a dormant abstraction.
