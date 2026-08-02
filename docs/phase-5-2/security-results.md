# Phase 5.2 security results

Date: 2026-08-02

## Dataset and environment

| Control | Result |
|---|---|
| Private production incidents | 0 |
| Ministry/MHRS data | 0 |
| Real credentials or PII | 0 |
| Locked identifiers/text | 0 prohibited overlaps |
| Prompt-injection training rows | 50 controlled rows |
| Unknown target IDs | 0 |
| Hidden reasoning in targets | 0 |
| Model network fallback | disabled; no model download attempted |
| Adapter/model publication | none |

The dataset generator is deterministic and cannot read locked corpora. The audit
enforces challenge checksum, exact normalized fields, locked identifiers,
template families, signatures, and a field-level character 5-gram threshold.

## Model behavior

Fine-tuned prompt injection, leakage, fabricated identifier, unsupported-claim,
and secret tests are `NOT_RUN` because no adapter exists. Historical P0/P3
security results are not relabeled as fine-tuned evidence.

## Residual risks

- The synthetic dataset can be semantically closer to locked categories than
  lexical metrics detect.
- A future locally supplied official checkpoint remains a complex untrusted
  artifact and requires checksum/provenance verification before loading.
- Training libraries execute native CUDA code with the WSL user's permissions.
- Qwen3.5 hybrid-layer optional kernels were not installed or tested.
- Adapter redistribution and merged-weight rights were not reached because no
  adapter or merged artifact exists.
