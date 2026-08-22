# Phase 5.3 progress

Date: 2026-08-22

## Objective

Explain or tightly bound the evaluation discrepancy between the pinned llama.cpp
GGUF path and the official Transformers path, diagnose the exposed Phase 5.2
failures, and determine whether a genuinely family-separated real-world corpus can
justify one future bounded alignment run. Phase 5.3 performs no training.

## Current gates

| Gate | State |
|---|---|
| Gate A: evaluation-path parity | PASS; discrepancy explained and bounded |
| Gate B: failure taxonomy and contract diagnostics | PASS; T1 contract selected |
| Gate C: real-world corpus and leakage audit | PASS; corpus frozen |
| Gate D: future bounded-training recommendation | NOT_RUN |

## Preserved starting point

- Phase 5.2 continuation is locally tagged `phase-5-2-complete` at
  `c497ce9c67479115215e28f617a535f50e5f0f22`.
- The active branch is `phase-5-3-evaluation-parity`.
- The deterministic Java product remains ready; the selected F1 adapter remains
  `EXPERIMENTAL`; no merged model or fine-tuned GGUF exists.
- Model and native runtime files remain ignored laboratory artifacts outside Git.
- No remote is configured and no push occurred.

## Baseline verification

- Java 21 reactor: PASS, 11 modules, 374 Surefire and 11 Failsafe tests, zero
  failures, errors, or skips.
- Research lock, Python compilation, and research tests: PASS, 10/10.
- Official checkpoint, official tokenizer, F1 adapter, prior GGUF, pinned
  `llama-completion`, P0, P3, and the current validator are present and identified.
- The development-only parity source is a deterministic 40-case selection from
  Benchmark V2 development: every development restraint and unsupported-claim
  trap, at least three cases per domain, then domain round-robin selection to 40.
  It contains no V2 holdout, Phase 5 blind, or post-training challenge case.

## Active stop rules

- Do not inspect or execute any new untouched final split in Phase 5.3.
- Do not train F3 or any other adapter.
- Do not change locked expected answers or weaken the semantic validator.
- Do not recommend future training unless evaluation parity and the new corpus are
  both evidence-backed.

## Gate A result

The baseline discrepancy is explained by four controlled effects: request-scoped
llama.cpp grammar versus unconstrained generation, severe NF4 base degradation,
development-versus-locked distribution differences, and measurable
third-party-versus-official conversion behavior. Exact chat-template text matched;
one prompt-file newline token was corrected but did not explain aggregate scores.

Official BF16, NF4, F1, third-party Q4_K_M, official-converted BF16, and
official-converted Q4_K_M paths were all measured on the same 40 development cases.
The full matrix is in `evaluation-parity.md`.

## Gate C result

The new corpus freezes 120 families: 90 controlled real runtime/integration and 30
controlled synthetic, split 70/20/30 by family. TRAIN and VALIDATION contain two
variants per family and use ordinal selection-only contract T1. Two independent
exports were byte-identical, all 120 families passed two ground-truth review passes,
and the final offline leakage audit reported zero prohibited findings. The 30 final
families were not executed or rendered.
