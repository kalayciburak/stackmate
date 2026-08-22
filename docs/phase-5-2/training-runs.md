# Phase 5.2 training runs

Date: 2026-08-02

## Resumed continuation result

`PASS` for the bounded training execution. The exact official checkpoint was
available only after the controlled acquisition continuation. The smoke test used
three optimizer steps and passed model load, NF4 attachment, finite forward/backward
loss, adapter save/reload, and valid JSON generation. Smoke output is not a quality
result.

Both primary runs used the inspected 12 text projection targets, NF4 with double
quantization, BF16, batch 1, gradient accumulation 16, gradient checkpointing,
sequence limit 2,048, seed 520021, and differed only in rank/alpha.

| Run | Trainable / architecture params | Trainable % | Steps | Train loss | Validation loss | Peak VRAM | Adapter bytes | Adapter SHA-256 | Result |
|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| F1 `r8/a16` | 8,409,600 / 2,213,241,664 | 0.3799675% | 100 | 0.0001428979 | 0.2934579 | 4,300,829,696 | 53,703,972 | `6642b7e3bad7eb64c6199d56db60935aabda54b5a2c26f33af4e79bc8d5ab6ca` | PASS |
| F2 `r16/a32` | 16,819,200 / 2,213,241,664 | 0.7599351% | 100 | 0.0551323 | 0.3996207 | 4,367,537,152 | 87,342,877 | `6bb781fcc8227ea94cd31f208bd48c6a3144b95d9b0be1c90c7d7c8db265d724` | PASS |

F1 completed after a resumable infrastructure timeout at checkpoint 50; the
checkpoint was preserved and the run resumed without changing configuration or
data. F2 completed without resume. F1 and F2 each produced 200/200 valid
validation outputs, 200/200 cause/check accuracy, 50/50 validation abstention,
zero unsupported claims, zero unknown IDs, zero generic fallback misuse, and
zero confusion. F1 was selected because the metrics tied and its adapter is
smaller.

Machine-readable run reports are `F1-qlora-r8.json` and `F2-qlora-r16.json`.

## Historical pre-acquisition result

`NOT_RUN — ENVIRONMENT BLOCKED` before the checkpoint-acquisition continuation.


The isolated WSL2 Python/CUDA stack passed. Training did not start because the
official `Qwen/Qwen3.5-2B` safetensors checkpoint at revision
`15852e8c16360a2fea060d615a32b45270f8a8fc` is not locally available, while the
phase contract explicitly excludes model download.

| Run | Rank | Target modules | LR | Epochs | Training time | Peak VRAM | Loss | Validation | Adapter | Result |
|---|---:|---|---:|---:|---:|---:|---:|---|---:|---|
| F0 | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | NOT_RUN; official base absent |
| F1 | 8 | NOT_INSPECTED | `1e-4` planned | 2 planned | n/a | n/a | n/a | n/a | n/a | NOT_RUN |
| F2 | 16 | NOT_INSPECTED | `1e-4` planned | 2 planned | n/a | n/a | n/a | n/a | n/a | NOT_RUN |

No third run was defined. No checkpoint, trainer state, adapter, optimizer state,
or model output exists.

The two planned runs differ only in LoRA rank/alpha and output directory. Both
freeze dropout 0.05, NF4 with double quantization, BF16 compute, two epochs,
learning rate `1e-4`, batch size 1, gradient accumulation 16, gradient
checkpointing, sequence length 2,048, and seed 520021. Target modules must come
from offline inspection of the exact official checkpoint; they were not guessed
from the third-party GGUF.

## Stop evidence

The offline preflight verified all pinned package versions, CUDA 13.0, BF16,
compute capability 8.9, the dataset lock, and the challenge lock, then exited 3
with only `BASE_MODEL_PATH_NOT_SET`.

The existing Q4_K_M file is not a compliant replacement. With an in-process
workaround for a Transformers package-metadata lookup bug, Transformers 5.14.1
reached the GGUF parser and returned:

```text
GGUF model with architecture qwen35 is not supported yet.
```

Even if that loader worked, dequantized third-party GGUF weights would not be the
pinned official safetensors base required by this controlled experiment. The
artifact was not modified and no alternate training stack was substituted.
