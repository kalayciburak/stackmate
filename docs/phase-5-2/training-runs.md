# Phase 5.2 training runs

Date: 2026-08-02

## Result

`NOT_RUN — ENVIRONMENT BLOCKED`

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
