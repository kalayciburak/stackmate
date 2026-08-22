# Phase 5.2 training reproducibility

Date: 2026-08-02

## Resumed continuation record

The historical missing-checkpoint record below is retained. The continuation used
the same lock files and exact model revision, then recorded these additional
digests:

| Item | Value |
|---|---|
| Base manifest SHA-256 | `1dc49db3fd0a49ee7bf33190b04c1bac5d1ed489e87855a81c64ac7344571981` |
| Architecture report SHA-256 | `bbc77177898f6de14dd26f96f7e6315c52b7c9fc16ee0b0ab13510f20909810f` |
| F1 config SHA-256 | `15cd5a948c78ef20e8799da57eea1ca63adef2f622b617d7e59ae02403d6a6e5` |
| F2 config SHA-256 | `3576ee8d64099b80d8b9fdaaac4b3b10eacd404f456860e1eae660e6cca76e03` |
| Environment config SHA-256 | `b81c1ca678e7a8c30ad8b2906bd571416baf5a5b47af31ab7a61c1c01025d91a` |
| Continuation preflight report SHA-256 | `1edb33ca909a5e8d881a5409fe27ff9f555a6cdf9fd9d04bea6c5b0438dc2049` |
| Dataset lock SHA-256 | `5b9d518b79c2616f4a5c0feac493495810f01b13767234728967eef6a4f868d4` |
| F1 adapter SHA-256 | `6642b7e3bad7eb64c6199d56db60935aabda54b5a2c26f33af4e79bc8d5ab6ca` |
| F2 adapter SHA-256 | `6bb781fcc8227ea94cd31f208bd48c6a3144b95d9b0be1c90c7d7c8db265d724` |
| Locked input export SHA-256 | `b036f1cc18e4ef0bd99f3de68466fb4e35cb0229855360be592afb1020af0f87` |
| Locked raw report SHA-256 | `a95c415d75ed5d52ff719e4007366a74c5bdb8c22df4c6238ed6bff4b73f0a90` |

No third quality run was created. The selected adapter was locked in local commit
`cc8af67`; the locked result failed and no merge/export path was entered.

## Pinned inputs

| Item | Value |
|---|---|
| Base repository | `Qwen/Qwen3.5-2B` |
| Base revision | `15852e8c16360a2fea060d615a32b45270f8a8fc` |
| Tokenizer revision | same revision; loaded and audited locally |
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
same seed. The inspected text targets were `down_proj`, `gate_proj`, `in_proj_a`,
`in_proj_b`, `in_proj_qkv`, `in_proj_z`, `k_proj`, `o_proj`, `out_proj`, `q_proj`,
`up_proj`, and `v_proj`; visual/output modules were excluded.

## Historical unavailable-measurement record

The measurements were unavailable only in the pre-acquisition report. They are
now recorded in `training-format.json`, `F1-qlora-r8.json`, `F2-qlora-r16.json`,
and the checkpoint manifest. Merged-model and GGUF checksums remain unavailable
because Gate C failed and export was correctly not run.

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

That historical command remains represented by `reports/environment-preflight.json`:
`BLOCKED`, exit 3, sole failure `BASE_MODEL_PATH_NOT_SET`, with no model network
access. The resumed command used the verified checkpoint and wrote its independent
result to `reports/environment-preflight-continuation.json`:
`PASS`, exit 0, with no network access.

```bash
STACKMATE_BASE_MODEL_DIR="$HOME/.cache/stackmate/models/models--Qwen--Qwen3.5-2B/snapshots/15852e8c16360a2fea060d615a32b45270f8a8fc" \
HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 \
UV_PROJECT_ENVIRONMENT="$HOME/.cache/stackmate/environments/phase-5-2" \
  uv run --frozen python scripts/environment_preflight.py \
  --config configs/environment-v1.json \
  --report reports/environment-preflight-continuation.json
```

## Verification results

- `uv lock --check --python 3.10`: PASS; 64 packages resolved.
- Python syntax plus isolated research tests: PASS, 10/10.
- `./mvnw clean verify` under Java 21: PASS, 374 Surefire plus 11
  Failsafe tests, zero failures/errors/skips, 11 reactor modules, 1:05.
- Official base-model cache: acquired and SHA-256 verified.
- F1/F2 executions: PASS; no training, checkpoint, or inference process remains.
- Locked qualification: executed once; Gate C failed on valid-structured and
  cause/check accuracy gates.
