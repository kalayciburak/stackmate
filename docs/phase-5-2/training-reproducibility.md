# Phase 5.2 training reproducibility

Date: 2026-08-02

## Pinned inputs

| Item | Value |
|---|---|
| Base repository | `Qwen/Qwen3.5-2B` |
| Base revision | `15852e8c16360a2fea060d615a32b45270f8a8fc` |
| Tokenizer revision | same revision; NOT_LOADED |
| Python | 3.10.12 |
| uv | 0.12.1 |
| PyTorch | 2.13.0+cu130 |
| Transformers | 5.14.1 |
| PEFT | 0.20.0 |
| Accelerate | 1.14.0 |
| bitsandbytes | 0.50.0 |
| gguf Python package | 0.19.0 |
| GPU | NVIDIA RTX 4070 Ti SUPER, 16,376 MiB, sm_89 |
| Driver / torch CUDA | 610.88 / 13.0 |
| Dataset manifest | `b433fd0091594570aaf8a973b7bbc0b9eb77dca5682b01d010ff8071c73ea2f2` |
| Challenge manifest | `9b5477bf2e1ea1593b3559a2696a0f5e9043f8d34577fbc86be85663d3d6f5e1` |
| Training seed | 520021 |
| Schema / evaluation prompt | 2 / P3 `phase5.1-p3-v1` |
| Context / output limit | 2,048 / 192 for qualification |
| pyproject SHA-256 | `fd3cc2ff13bed66e13e9a6b23ff4080c601c8925e4288c47e639c54d4535e708` |
| uv.lock SHA-256 | `2c0575992628ec489cb9245fb546b4f2b7a35b5082b8d03950093255b475ca45` |

F1 and F2 freeze rank 8/16, alpha 16/32, dropout 0.05, NF4 double
quantization, BF16 compute, two epochs, `1e-4` learning rate, batch size 1,
gradient accumulation 16, gradient checkpointing, sequence limit 2,048, and the
same seed. Target modules are deliberately `NOT_INSPECTED`; the actual official
checkpoint must be loaded offline before selecting only present modules.

## Unavailable training measurements

Chat-template checksum, token-length statistics, trainable parameter count,
training steps/loss/gradient norm/tokens per second, peak training VRAM, adapter
checksum/size, merged-model checksum, and GGUF checksum are `NOT_AVAILABLE`.
They require the prohibited-missing official checkpoint and are not inferred from
the third-party GGUF.

## Commands

```bash
python3 -m pip install --user --disable-pip-version-check uv==0.12.1
cd research/model-alignment
uv lock --python 3.10
UV_PROJECT_ENVIRONMENT="$HOME/.cache/stackmate-phase5-2-venv" uv sync --frozen
UV_PROJECT_ENVIRONMENT="$HOME/.cache/stackmate-phase5-2-venv" \
  uv run --frozen python scripts/environment_preflight.py \
  --config configs/environment-v1.json \
  --report reports/environment-preflight.json
```

Actual preflight result: `BLOCKED`, exit 3, sole failure
`BASE_MODEL_PATH_NOT_SET`. It attempted no model network access.
