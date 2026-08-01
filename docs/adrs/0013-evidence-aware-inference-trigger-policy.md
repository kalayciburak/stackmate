# ADR 0013: Evidence-aware inference trigger policy

- Status: Accepted
- Date: 2026-08-01

## Context

Running a CPU model for every captured exception consumes seconds and substantial memory even when deterministic
evidence already identifies an exact failure. Conversely, asking a small model to explain low or insufficient evidence
encourages invention. Local inference should run only where bounded explanation or prioritization can add value.

## Decision

Add the typed `stackmate.inference.trigger-policy` setting with `NEVER`, `ALWAYS`, and
`WHEN_ENHANCEMENT_IS_USEFUL`. The last value is the default.

The default policy runs inference only for a non-fallback diagnosis whose primary deterministic strength is `MEDIUM`.
It skips `HIGH` because the exact deterministic diagnosis is already actionable, and it skips `LOW`, `UNKNOWN`, and
fallback diagnoses because the small model has insufficient evidence. A policy skip happens before executor submission
and is not recorded as a model failure. `ALWAYS` exists for controlled benchmarks and explicit user choice; `NEVER`
disables model work without changing deterministic diagnostics.

## Consequences

- High-confidence incidents incur no model startup, queue, latency, or memory cost by default.
- Low-evidence incidents remain honestly limited instead of receiving speculative prose.
- Normal duplicate reservation still records that the policy decision was made within the TTL; skipped incidents do not
  repeatedly probe the executor.
- Packaged fake-child and real-model acceptance profiles explicitly use `ALWAYS` because they test protocol behavior,
  not the production default.

## Rejected alternatives

- Infer for every incident: poor value-to-cost ratio and higher hallucination exposure.
- Infer only for low evidence: asks the model to solve the cases with the least trustworthy context.
- Let the model decide whether it should run: impossible without paying model startup and inference cost first.
