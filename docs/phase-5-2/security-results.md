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
| Model network fallback | disabled; only the controlled official checkpoint acquisition used network |
| Adapter/model publication | none |

The dataset generator is deterministic and cannot read locked corpora. The audit
enforces challenge checksum, exact normalized fields, locked identifiers,
template families, signatures, and a field-level character 5-gram threshold.

## Model behavior

The resumed F1 adapter and all four locked comparison conditions were executed
locally. The authoritative validator reported, for A1, zero accepted unsupported
claims, zero accepted fabricated references, zero accepted unknown IDs, zero
secret leakage, zero prompt-injection success, and zero hidden thinking. A1 did
generate 19 outputs containing unknown IDs, but every one was rejected and none was
accepted. These are locked-evaluation observations, not a production safety claim.

The locked qualification still failed because A1 achieved only 44/70 cause and
45/70 check accuracy and 68/70 valid structured output. Historical P0/P3 results
are not relabeled as fine-tuned evidence. No adapter or model was published.

## Resumed acquisition and trust-boundary controls

- Only the official Hugging Face `Qwen/Qwen3.5-2B` repository at the immutable
  revision was accessed for model bytes.
- The downloader used bounded workers, resumable cache behavior, safetensors-only
  filtering, and recorded file sizes and SHA-256 values.
- SafeTensors, tokenizer resources, and the QLoRA libraries were treated as local
  trust-boundary inputs; `trust_remote_code=false`, offline loading, and no
  telemetry/experiment tracker were used.
- The model, adapters, optimizer/checkpoint state, locked export, and reports with
  absolute WSL paths remain outside the Git repository where applicable. No upload,
  publication, or remote inference occurred.
- The new research scripts are not Maven runtime dependencies. The ordinary Java
  product has no model downloader or model-loading path.

## Residual risks

- The synthetic dataset can be semantically closer to locked categories than
  lexical metrics detect.
- A future locally supplied official checkpoint remains a complex untrusted
  artifact and requires checksum/provenance verification before loading.
- Training libraries execute native CUDA code with the WSL user's permissions.
- Qwen3.5 hybrid-layer optional kernels were not installed or tested.
- Adapter redistribution and merged-weight rights were not reached because no
  adapter or merged artifact exists.
