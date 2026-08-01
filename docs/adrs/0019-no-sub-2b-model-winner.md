# ADR 0019: No sub-2B model winner

- Status: Accepted
- Date: 2026-08-02

## Context

Phase 4 screened three eligible English instruction models below 2,000,000,000 total
parameters using strict Q4_K_M llama.cpp generation and the unchanged evidence-only
contract. The exact Phase 3 Qwen3.5 reference was retained as an ineligible control
after official metadata showed 2,274,069,824 total parameters.

## Decision

Declare Gate B failed and select no finalist:

- SmolLM2-360M passed aggregate ID thresholds at their boundary but produced 0/20
  useful explanations and only 80%/80% holdout selections.
- Qwen3-0.6B produced useful prose and 95%/90% aggregate selections, but holdout
  check accuracy was 80%, below the mandatory 85% gate.
- Qwen2.5-1.5B produced only 19/20 valid outputs and missed selection gates.
- Qwen3.5-2B passed the English screen but is excluded by the hard parameter cap.

Do not run Gate C, do not create a production llama.cpp adapter, and do not provision
models. Keep deterministic diagnostics `READY` and local inference `NOT_VIABLE`.

## Consequences

- Safety/quality thresholds and the parameter cap remain unchanged.
- Fast and memory-efficient schema compliance is not marketed as useful inference.
- Historical Jlama and llama.cpp research remains isolated.
- The recommended next product direction is public-alpha hardening of deterministic
  diagnostics, not a larger unapproved model.
