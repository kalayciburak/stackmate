# ADR 0018: Sub-2B English model tournament

- Status: Accepted for screening
- Date: 2026-08-02

## Context

Phase 3 showed that quantized Qwen3.5-2B could satisfy a small English screen, but
the product remained blocked by scope, provenance, and lack of full worker evidence.
Phase 4 asks whether a smaller English-only model is sufficient for StackMate's
explanation-and-ID-selection contract.

Official metadata also revealed that two marketing names obscure the strict cap:
`Qwen3-1.7B` has 2,031,739,904 total parameters and `Qwen3.5-2B` has 2,274,069,824
including its vision component. Neither is eligible under the 2,000,000,000 total
parameter limit.

## Decision

Screen three eligible Q4_K_M candidates in ascending size order:

1. SmolLM2-360M-Instruct (361,821,120 parameters).
2. Qwen3-0.6B (751,632,384 parameters).
3. Qwen2.5-1.5B-Instruct (1,543,714,304 parameters).

Run the exact Phase 3 Qwen3.5-2B artifact as an ineligible historical control to
satisfy the required reference comparison, but never allow it to advance or win.
This resolves the conflict between the requested reference and the stricter explicit
total-parameter rule without silently redefining total parameters.

Use the frozen 20-case V3 screen, prompt/schema version 2, context 2,048, maximum 192
output tokens, strict JSON grammar, unchanged semantic validation, and no more than
two generation profiles per model. Only chat-envelope syntax may vary to match each
official tokenizer template.

The isolated CLI runtime is official llama.cpp `b10221`, commit
`815a2a5915f22ce6a760c676389c5dfe8535c08f`. Only candidates with 20/20 valid trusted
outputs and every mandatory safety gate may enter Gate C. The smallest candidate that
passes later full-corpus and persistent-worker gates wins.

## Consequences

- Marketing parameter labels are never used as eligibility evidence.
- Qwen3-1.7B is rejected without downloading its GGUF.
- Third-party GGUFs remain research inputs and cannot become production catalog
  entries on current provenance.
- A failed screen is retained as evidence; validation is not weakened to improve a
  score.
- Gate D and any production adapter remain forbidden until Gate C produces a
  `READY_CANDIDATE`.
