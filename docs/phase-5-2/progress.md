# Phase 5.2 progress

Date: 2026-08-02

## Current state

| Gate | State |
|---|---|
| Gate A: data feasibility and leakage control | PASS |
| Gate B: bounded adapter training | PASS; F1/F2 completed |
| Gate C: locked qualification | FAILED; mandatory accuracy/structured-output gates |
| Gate D: GGUF compatibility | NOT RUN; Gate C failed |

## Resumed continuation result

The historical missing-checkpoint stop was resumed without changing the base model,
frozen data, locked cases, or planned run matrix. The exact official checkpoint was
downloaded and verified at revision
`15852e8c16360a2fea060d615a32b45270f8a8fc`. The local preflight, architecture
inspection, tokenization audit, NF4/BF16 smoke test, F1, F2, and validation-only
adapter selection all passed their infrastructure gates.

F1 (`r=8`) and F2 (`r=16`) tied on every validation metric; F1 was locked because
its adapter is smaller. The selection document was committed locally as
`cc8af67` before any locked output was generated.

The locked comparison then ran once over 20 V2 holdout, 20 blind, and 30 challenge
cases, comparing B0/base+P0, B1/base+P3, A1/F1+P3, and A2/F1+P2. The authoritative
production-validator score for A1 was:

| Stage | Valid structured | Cause | Check |
|---|---:|---:|---:|
| Benchmark V2 holdout | 19/20 | 11/20 | 11/20 |
| Phase 5 blind | 20/20 | 15/20 | 15/20 |
| Post-training challenge | 29/30 | 18/30 | 19/30 |
| Combined | 68/70 | 44/70 | 45/70 |

All accepted safety counters were zero, and restraint was 6/6 on the applicable
locked cases. The mandatory 85% accuracy and 98% valid-structured gates failed;
therefore full confirmation, merge, GGUF export, and compatibility qualification
were not run. The result is `EXPERIMENTAL`, not production-ready.

## Completed

- Confirmed the clean Phase 5.1 completion commit.
- Created local tag `phase-5-1-complete` at the reviewed commit.
- Created branch `phase-5-2-task-specific-alignment`.
- Reproduced the normal Java 21 reactor: 346 Surefire plus 11 Failsafe tests.
- Verified P3 and prompt-split checksum contracts.
- Measured host and WSL2 hardware instead of assuming it.
- Chose isolated WSL2 QLoRA as the provisional environment path.
- Inspected the official model, Transformers, PEFT, bitsandbytes and PyTorch support
  surfaces before choosing dependency versions.
- Froze a 30-case post-training challenge before adapter training.
- Reproduced 20/20 challenge runtime/integration recipes and validated all 30
  deterministic candidate contracts.
- Generated and locked 1,000 deterministic schema-v2 chat examples using a
  32-family train and 8-family validation split.
- Passed the locked-corpus leakage audit with zero prohibited overlap.
- Passed ten isolated Python dataset/evaluation-contract tests and 28 new Java
  challenge/dataset fixture tests.
- Reproduced P0/P3 on all 40 development cases with pinned model/runtime/split
  hashes; P3 matched historical validity, accuracy, and restraint exactly.
- Locked a 62-package WSL2 Python/CUDA environment; CUDA/BF16 smoke passed.
- Historical Gate B report: stopped because the official base checkpoint was
  absent, model download was out of scope, and Transformers rejected the existing
  `qwen35` GGUF loader. The checkpoint acquisition continuation is documented
  separately and does not erase that historical report.
- Passed the final 11-module Java 21 reactor with 374 Surefire plus 11 Failsafe
  tests (385 total), zero failures, errors, or skips, and no Python/model input.

## Next controlled actions

No dependent Phase 5.2 export action remains after the failed locked gates. Full
confirmation, merge, quantization, and GGUF qualification were intentionally not
run. Recommended next phase: public alpha hardening.

Historical Phase 4, Phase 5 and Phase 5.1 reports remain unchanged.
