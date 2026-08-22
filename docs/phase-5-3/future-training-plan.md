# Phase 5.3 future bounded-training plan

Date: 2026-08-22

Status: **JUSTIFIED, NOT EXECUTED**

## Decision

Gates A, B, and C pass. The unchanged F1 adapter fails the new validation contract,
while the new corpus is family-separated, reproducible, reviewed, and leakage-free.
Exactly one future QLoRA run is therefore justified. Phase 5.3 creates no run
configuration, adapter, optimizer state, or model export and performs no training.

## Predeclared future run

| Setting | Value |
|---|---|
| Base | official `Qwen/Qwen3.5-2B` revision `15852e8c16360a2fea060d615a32b45270f8a8fc` |
| TRAIN / VALIDATION | frozen `stackmate-real-world-v1`, 140 / 40 rows |
| Model-facing contract | T1 selection-only zero-based ordinals |
| LoRA rank / alpha / dropout | 8 / 16 / 0.05 |
| Learning rate | `1e-4` |
| Maximum epochs | 6 |
| Quantization | NF4 with double quantization |
| Compute | BF16 |
| Batch / gradient accumulation | 1 / 16 |
| Gradient checkpointing | enabled |
| Maximum sequence | 2,048 |
| Seed | 520021 |
| Reporting | local only; no telemetry or tracker |

Six epochs are a compute bound, not a tuned optimum. The old F1 run processed
about 664,992 forward tokens over two epochs; six epochs of the frozen new TRAIN
split would process about 691,080. This keeps forward-token exposure comparable
while limiting the new run to about 54 optimizer steps. The new targets are shorter,
so validation must detect underfit or overfit rather than authorizing another run.

## Checkpoint selection

Evaluate only the 40-row VALIDATION split after each epoch. Lock the earliest
checkpoint that meets every validation gate below. If none does, select the single
best checkpoint by the listed order only for honest classification; do not run a
second configuration.

1. Unknown/out-of-range indexes: 0.
2. Generic fallback misuse: 0.
3. Valid exact T1 structure: 40/40.
4. Strict restraint: at least 20/22.
5. Joint cause/check accuracy: at least 34/40.
6. Family order invariance: at least 18/20.
7. Lower validation loss, then earlier epoch.

## Locked-final boundary

Only after one checkpoint is selected and committed by hash may the 30 sealed final
families be executed once. Do not alter the adapter, T1 prompt, validator,
generation settings, or expected answers after viewing them. Mandatory final gates:

- valid exact T1 output: 30/30;
- unknown/out-of-range indexes and generic fallback misuse: 0;
- strict restraint: at least 9/10;
- joint cause/check accuracy: at least 26/30;
- no hidden thinking or model prose.

Failure ends the future experiment. No F2/F3 sweep, prompt tuning against final,
merge, GGUF export, persistent worker, provisioning, or production integration is
authorized by this plan.
