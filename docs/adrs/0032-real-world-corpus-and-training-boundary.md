# ADR 0032: Adopt the family-separated real-world research corpus

Date: 2026-08-22

Status: Accepted

## Context

Phase 5.2 adapter selection succeeded on deterministic synthetic validation but
failed locked runtime-oriented qualification. Phase 5.3 Gate A bounded runtime and
quantization effects, while Gate B showed that selection-only ordinal contract T1
removes unknown-ID failures. A future training recommendation still required a
materially different, leakage-controlled dataset.

## Decision

Adopt `stackmate-real-world-v1` as the only eligible corpus for a future bounded
alignment experiment.

- Freeze 120 semantic families before any future training: 70 TRAIN, 20
  VALIDATION, and 30 LOCKED_FINAL.
- Use 90 controlled real runtime/integration families and 30 narrowly justified
  synthetic topology/selection families.
- Generate exactly two development variants per family; never cross a family or
  trigger primitive between splits.
- Train contract T1 only: request-local ordinal selections and a limitation, with
  no model-written prose target.
- Keep Java responsible for ID mapping, semantic validation, and deterministic
  user-facing prose.
- Keep LOCKED_FINAL sealed and unexecuted throughout Phase 5.3.
- Require the corpus manifest, two-pass review, two byte-identical exports, and
  offline leakage audit to match their recorded hashes before any future use.

## Consequences

The new corpus is a stronger experimental basis than the Phase 5.2 synthetic data,
but does not authorize training in Phase 5.3 and does not make F1 or Qwen3.5-2B
ready. A future run, if Gate D recommends one, must be one bounded continuation and
must not tune against LOCKED_FINAL results. Testcontainers and PostgreSQL remain
explicit benchmark test dependencies; product runtime modules remain unchanged.
