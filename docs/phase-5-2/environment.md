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
| uv | not initially installed |
| CUDA toolkit / nvcc | not initially installed |
| PyTorch / Transformers / PEFT | not initially installed |
| Official Qwen3.5-2B HF cache | absent |

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

No training claim is made until the pinned environment passes an actual CUDA,
4-bit-load, adapter-reload, and JSON-generation smoke test.
