# Phase 5.3 decisions

Date: 2026-08-22

## D1: Preserve Phase 5.2 as historical evidence

The completed continuation is tagged locally and is not rewritten. Existing locked
outputs are exposed diagnostic evidence only; they cannot become a new untouched
qualification gate.

## D2: Freeze a coverage-balanced 40-case V2 development parity split

Use only Benchmark V2 development cases. Include every development restraint and
unsupported-claim trap, guarantee at least three cases from each of the eight V2
domains, then fill to 40 by domain round-robin in source-manifest order. This fixed
rule represents Java, Spring, MVC, database, security, network, concurrency, and
adversarial behavior while excluding every holdout, blind, challenge, or other
final case.

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

## D6: Separate model selection from prose

Development ablations select `SEPARATE_SELECTION_AND_PROSE`: a future model emits
only request-local ordinal cause/check selections and a limitation; Java validates,
maps, and renders trusted deterministic text. This is a future research contract,
not a Phase 5.3 production-schema change. ADR 0031 records the evidence.

## D7: Freeze the family-separated real-world corpus

Adopt `stackmate-real-world-v1` under ADR 0032 after two independent ground-truth
passes, two byte-identical exports, and a zero-finding offline leakage audit. Keep
its 30 final families sealed and unexecuted in Phase 5.3. This establishes data
feasibility only; Gate D still owns any future one-shot training recommendation.
