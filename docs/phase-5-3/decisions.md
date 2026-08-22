# Phase 5.3 decisions

Date: 2026-08-22

## D1: Preserve Phase 5.2 as historical evidence

The completed continuation is tagged locally and is not rewritten. Existing locked
outputs are exposed diagnostic evidence only; they cannot become a new untouched
qualification gate.

## D2: Use only the frozen 40-case prompt-development split for parity

The existing `promptDevelopment` split already provides representative Java,
Spring, MVC, JDBC, restraint, hard-negative, and prompt-injection development
cases. Reusing it avoids a new case-selection degree of freedom and excludes every
locked or final case.

## D3: Treat runtime, weights, serialization, extraction, and validation separately

Aggregate score differences do not establish runtime parity. Phase 5.3 records
rendered prompts, chat-template output, tokens, generation parameters, raw output,
extraction, semantic validation, and latency for each path before attributing a
failure to model semantics.

## D4: Keep production contracts unchanged during laboratory ablations

Ordinal selection, per-candidate decisions, candidate reordering, and
selection-versus-prose separation are development-only experiments. The production
schema and validator remain unchanged unless one contract clearly dominates and a
separate accepted ADR records the decision.

## D5: No training in Phase 5.3

F1 remains the only selected adapter. Phase 5.3 creates no F3 configuration and
runs no LoRA or QLoRA training. A future one-shot recommendation is permitted only
if evaluation parity is explained or bounded and a 120-family real-world corpus
passes family separation, ground-truth review, and leakage controls.
