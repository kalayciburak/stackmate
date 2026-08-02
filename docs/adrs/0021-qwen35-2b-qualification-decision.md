# ADR 0021: Qwen3.5-2B is not qualified

Status: Accepted

Date: 2026-08-02

## Context

The exact pinned Qwen3.5-2B Q4_K_M artifact passed Phase 4's 20-case control. Phase 5 evaluated it against the
frozen 80-case Benchmark V2 corpus and a new frozen 20-case blind corpus with unchanged explanation-only ownership,
strict grammar, semantic validation, and predeclared thresholds.

## Decision

Classify the candidate as `NOT_VIABLE` for StackMate's supported local-explanation path. Do not run persistent-worker
qualification, build a production llama.cpp adapter, or add packaged Spring integration.

## Evidence

- 100/100 valid structured outputs; 94/100 exact cause and check selection.
- V2 holdout and blind cause/check selection were each 19/20.
- Strict unknown/insufficient restraint passed only 1/11 because ten outputs omitted structured limitations and
  four selected generic IDs where no selection was expected.
- Manual review found three accepted unsupported technical alternatives. The mandatory budget is zero.
- Manual usefulness and value-add thresholds passed, but cannot override mandatory safety requirements.

## Consequences

- Deterministic diagnostics remain `READY` and unchanged.
- No new runtime dependency, native executable, model file, network path, or auto-configuration enters the product.
- The test-only qualification harness and reports remain as reproducible negative evidence.
- Any future reconsideration requires a new frozen evaluation and a stronger semantic-containment strategy; it may
  not retroactively relax this decision.
