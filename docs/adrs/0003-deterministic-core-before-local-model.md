# ADR 0003: Deterministic core before local model

- Status: Accepted
- Date: 2026-08-01

## Context

Raw exception text is noisy, unbounded, potentially sensitive, and may be attacker controlled.
A small local model is not a reliable source of facts and may be absent, slow, invalid, or unavailable.

## Decision

Build and verify the deterministic incident pipeline before integrating a production inference engine.
Only immutable, bounded, sanitized, evidence-backed facts may reach inference. Diagnostic strength is
computed from deterministic evidence; model self-confidence is ignored.

## Consequences

- StackMate remains useful without a model.
- The benchmark compares raw-model, structured-model, and deterministic modes without distorting the core.
- Invalid, timed-out, or unavailable inference always falls back to the deterministic diagnosis.
- The model may explain or prioritize only pre-declared facts, cause candidates, and check candidates.
