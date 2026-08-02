# Phase 5.1 progress

Updated: 2026-08-02

## Objective

Determine through a controlled prompt ablation whether the pinned Qwen3.5-2B
Q4_K_M model can meet StackMate's semantic-restraint standard without changing the
model, runtime, schema, corpus, ground truth, generation profile, hardware, or
primary-comparison validator.

## Entry state

- Reviewed Phase 5 completion commit:
  `d29d789933a6394db942435e2c5ef607cda53f2f`.
- Created local annotated tag `phase-5-complete` at that commit.
- Created local branch `phase-5-1-prompt-alignment`.
- Confirmed the starting tree was clean and no remote is configured.
- Read the current product, Phase 4/5, model-selection, security, prompt, repair,
  schema, validator, corpus, and llama.cpp qualification materials.

## Baseline reproduction

- Java 21 `clean verify`: 11/11 reactor projects, 332 Surefire plus 11 Failsafe
  tests, zero failures/errors/skips.
- Pinned 100-case P0 run: 100/100 valid, 94/100 exact cause and check selection,
  V2 holdout 19/20, blind 19/20.
- Strict restraint reproduced at 1/11: ten missing limitations and four generic
  fallback selections.
- The same three unsupported technical alternatives were reproduced verbatim.
  The existing automatic counter still reports zero, confirming the semantic
  detector gap rather than resolving it.
- No repair, timeout, crash, leakage, injection success, unknown ID, fabricated
  reference, hidden thinking, or residual workspace process occurred.

## Current gate

The current English system and repair prompts are being audited before the frozen
development/selection/locked split and P1-P3 resources are created.

## Out-of-scope status

Persistent worker: `NOT_RUN`. Production adapter: `NOT_IMPLEMENTED`.
Provisioning: `NOT_IMPLEMENTED`. Remote push: none.
