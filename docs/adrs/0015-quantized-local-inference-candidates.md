# ADR 0015: Quantized local inference candidates

- Status: Accepted for feasibility testing
- Date: 2026-08-01

## Context

Phase 2 classified two BF16 Qwen2.5 0.5B/Jlama combinations as `NOT_VIABLE`. The
failure did not invalidate the deterministic product, but it left open whether a
larger quantized model with constrained decoding could satisfy StackMate's reduced
explanation-only contract.

The repository already contains ADRs numbered 0009 through 0014 from Phase 2. Phase
3 therefore continues at 0015 rather than overwriting historical decisions.

## Decision

Gate A will evaluate exactly:

1. `Qwen/Qwen3.5-2B` as LITE, Q4_K_M, text-only and non-thinking.
2. `Qwen/Qwen3.5-4B` as QUALITY, Q4_K_M, text-only with thinking explicitly disabled.

The official model revisions and licenses are pinned in
`docs/phase-3/model-runtime-research.md`. Official Q4_K_M GGUF repositories were not
available at research time. Bartowski GGUF revisions may therefore be used only as
third-party feasibility inputs after byte-size and SHA-256 verification.

The model may explain facts and select supplied IDs only. It cannot generate or
override category, root type, evidence strength, diagnostic facts, source locations,
candidate causes, or candidate checks. Phase 2 validation remains unchanged.

## Consequences

- A successful screen proves only the quality hypothesis for the tested artifact and
  hardware.
- Third-party quantization provenance is insufficient for an automatic production
  model catalog without a later reproducible or first-party supply-chain path.
- No downloader, Spring adapter, or production module is built unless Gate A passes.
- If neither candidate passes, local inference remains `NOT_VIABLE` and Phase 3 stops
  before Gates B and C.
