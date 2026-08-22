# Phase 5.2 adapter selection

Date: 2026-08-02

This is the validation-only selection checkpoint for the resumed Phase 5.2
experiment. The 200-example frozen validation split was evaluated after both
planned runs completed. Benchmark V2 holdout, Phase 5 blind, and the 30-case
post-training challenge were not read.

## Runs compared

| Run | Adapter size | Valid structured | Cause | Check | Abstention | Unsupported | Unknown IDs | Fallback misuse | Confusion |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| F1 `r=8, alpha=16` | 53,703,972 B | 200/200 | 200/200 | 200/200 | 50/50 | 0 | 0 | 0 | 0 |
| F2 `r=16, alpha=32` | 87,342,877 B | 200/200 | 200/200 | 200/200 | 50/50 | 0 | 0 | 0 | 0 |

Both runs also produced 200/200 valid JSON outputs and 200/200 useful
explanations. F1 validation report: `reports/F1-validation.json`; F2
validation report: `reports/F2-validation.json`.

## Locked choice

The ordered selection criteria were applied exactly as specified. All quality
and safety metrics tied, so the final criterion selected the smaller adapter:

- Adapter: `F1-qlora-r8`
- Base: `Qwen/Qwen3.5-2B`
- Base revision: `15852e8c16360a2fea060d615a32b45270f8a8fc`
- Adapter path: `<WSL_CACHE>/adapters/phase-5-2/F1-qlora-r8`
- Adapter SHA-256: `6642b7e3bad7eb64c6199d56db60935aabda54b5a2c26f33af4e79bc8d5ab6ca`
- Training configuration SHA-256: `15cd5a948c78ef20e8799da57eea1ca63adef2f622b617d7e59ae02403d6a6e5`
- Dataset manifest SHA-256: `b433fd0091594570aaf8a973b7bbc0b9eb77dca5682b01d010ff8071c73ea2f2`
- Selection commit: `cc8af67` (local, not pushed)
- Selection source commit: `0a6c1f27a086457079d5011e4451a8810fabb21a`

The machine-readable lock is `research/model-alignment/reports/selected-adapter.json`.
Adapter weights remain outside Git. No additional training run is permitted
by this selection checkpoint.
