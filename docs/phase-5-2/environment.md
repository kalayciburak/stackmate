# Phase 5.2 environment assessment

Date: 2026-08-02

## Host and accelerator

| Item | Observed value |
|---|---|
| Host OS | Microsoft Windows 11 Pro 10.0.26200, 64-bit |
| CPU | AMD Ryzen 9 7950X, 16 cores / 32 logical processors |
| System RAM | 63.16 GiB |
| System disk | 930.41 GiB total / 570.19 GiB free |
| GPU | NVIDIA GeForce RTX 4070 Ti SUPER |
| VRAM | 16,376 MiB |
| Compute capability | 8.9 |
| NVIDIA driver | 610.88 |
| Driver CUDA capability | 13.3 |
| Initial GPU use | 1,285 MiB; display processes only |

The Windows host has no `nvcc` on `PATH`. No driver or system-wide toolkit change
will be made by this experiment.

## WSL2

| Item | Observed value |
|---|---|
| Distribution | Ubuntu 22.04.5 LTS |
| WSL version | 2 |
| Kernel | 6.18.33.2-microsoft-standard-WSL2 |
| GPU visibility | PASS; same GPU, VRAM, driver and compute capability |
| WSL memory | 30 GiB total plus 8 GiB swap |
| WSL root disk | 1,007 GiB total / 942 GiB available |
| Python | 3.10.12 |
| uv | 0.12.1, installed for the WSL user |
| CUDA toolkit / nvcc | not initially installed |
| PyTorch | 2.13.0+cu130 |
| Transformers / PEFT / Accelerate | 5.14.1 / 0.20.0 / 1.14.0 |
| bitsandbytes / gguf | 0.50.0 / 0.19.0 |
| Official Qwen3.5-2B HF cache | acquired and SHA-256 verified under WSL cache |

WSL2 is the selected research environment. The training environment will be
project-local and reproducibly locked; no dependency enters a Maven module.

## Compatibility decision

The official Qwen repository identifies `Qwen/Qwen3.5-2B` as an Apache-2.0,
4.57 GB Transformers safetensors checkpoint at revision
`15852e8c16360a2fea060d615a32b45270f8a8fc`. Current Transformers documentation
supports the Qwen3.5 architecture and notes that its hybrid DeltaNet layers use a
slower, more memory-hungry PyTorch fallback when optional fast kernels are absent.

Official Hugging Face documentation supports bitsandbytes NF4/FP4 on Linux x86-64
with NVIDIA Pascal or newer hardware and CUDA 11.8 through 13.0. The installed
Ada GPU exceeds the hardware requirement. A pinned PyTorch CUDA wheel may provide
its own CUDA runtime; absence of host `nvcc` is therefore recorded but is not by
itself treated as a QLoRA blocker.

The locked environment contains 62 resolved packages in an isolated
`$HOME/.cache/stackmate-phase5-2-venv`; no dependency enters Maven or a product
artifact. CUDA tensor execution passed, Torch reported CUDA 13.0, BF16 support,
compute capability 8.9, and 17,170,956,288 bytes of device memory.

## Historical Gate B blocker

The preflight passed package, CUDA, dataset-lock, and challenge-lock checks but
stopped with `BASE_MODEL_PATH_NOT_SET`. The required official safetensors
checkpoint is not cached anywhere in the inspected Windows/WSL locations, and
Phase 5.2 explicitly excludes model download.

The existing Q4_K_M GGUF cannot substitute safely. Its metadata reports
architecture `qwen35`, but Transformers 5.14.1 returns `GGUF model with
architecture qwen35 is not supported yet` after a diagnostic process-local fix
for its separate `gguf` version-detection bug. It is also a third-party lossy
quantization, not the official base checkpoint required by the experiment.

Therefore the environment itself is CUDA-capable, but the controlled training
input is unavailable. Four-bit load, target-module inspection, chat-template
statistics, checkpoint save/reload, loss, and JSON-generation smoke tests were
not run in the historical report.

## Resumed continuation

The same Ubuntu 22.04.5 WSL2 distribution was reused. No Linux NVIDIA driver was
installed; the Windows driver remained the WSL GPU provider. The only missing
ordinary Ubuntu package was `python3-venv`, installed after a package-index refresh
with `apt-get update` and `apt-get install -y python3-venv`. The frozen uv
environment then synchronized successfully with `uv sync --frozen`.

The official checkpoint was downloaded to the WSL Linux filesystem, never into the
Git checkout. Local model/tokenizer loading passed with offline mode,
`local_files_only=true`, and `trust_remote_code=false`. The existing CUDA/BF16
environment was reused; no Windows driver, BIOS, system CUDA toolkit, or unrelated
distribution was changed.
