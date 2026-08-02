# ADR 0020: Resource budgets replace the strict parameter cap

Status: Accepted

Date: 2026-08-02

## Context

Phase 4 excluded Qwen3.5-2B solely because its official total parameter count, 2,274,069,824, exceeded a strict
two-billion cap even though its small screen was promising. Parameter count is only an indirect proxy for the local
developer cost that matters.

## Decision

Qualification uses measured artifact size, worker RSS, parent-plus-worker RSS, model-load time, warm latency,
request-thread isolation, and process cleanup budgets. Semantic and security gates remain mandatory and cannot be
traded for resource performance.

## Consequences

- Qwen3.5-2B could enter Phase 5 qualification without being declared ready.
- Gate A failed on restraint and semantic safety before persistent-worker resource qualification, so no Phase 5
  RSS or warm-worker claim exists.
- Future models are not accepted by parameter class or marketing name; the exact artifact and measured execution
  remain the unit of evidence.
