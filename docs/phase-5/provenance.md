# Phase 5 model and runtime provenance

Date verified: 2026-08-02

## Upstream model

| Field | Value |
|---|---|
| Repository | `Qwen/Qwen3.5-2B` |
| Immutable revision | `15852e8c16360a2fea060d615a32b45270f8a8fc` |
| License | Apache-2.0 |
| Architecture | `Qwen3_5ForConditionalGeneration`; text model type `qwen3_5_text` |
| Official parameter metadata | 2,274,069,824 total parameters |
| Text layers / hidden size | 24 / 2,048 |
| Maximum context in upstream config | 262,144 tokens |
| Intended StackMate use | text-only, one turn, non-thinking, 2,048-token context |

Qwen documents the model as post-trained and non-thinking by default, with optional thinking controlled by its chat
template. Phase 5 still rejects any `<think>` content instead of stripping and accepting it.

## Qualification GGUF

| Field | Value |
|---|---|
| Source | Third-party feasibility conversion retained from Phase 3/4 |
| File | `Qwen_Qwen3.5-2B-Q4_K_M.gguf` |
| Bytes | 1,396,198,496 |
| SHA-256 | `57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8` |
| Reported architecture | `qwen35` |
| Reported file type | `Q4_K - Medium` |
| Reported text parameters | approximately 1.94B |
| Git status | ignored and untracked |

The GGUF is not an official Qwen-distributed artifact and is not eligible for automatic product distribution. The
runtime reports unused MTP block-24 tensors. This is recorded as a compatibility residual and must be assessed by
qualification rather than presented as harmless by assumption.

## Native runtime

| Field | Value |
|---|---|
| Project | `ggml-org/llama.cpp` |
| Release | `b10223` |
| Commit | `11924d4c17abc27383376a1ac6a24fa3e36c1c0c` |
| Artifact | `llama-b10223-bin-win-cpu-x64.zip` |
| Artifact bytes | 18,352,859 |
| GitHub-published SHA-256 | `74c1ded0512818d98b51940bf9150e16da8ed79cf0cbe8d85788e01cdd00ff67` |
| Local archive SHA-256 | `74c1ded0512818d98b51940bf9150e16da8ed79cf0cbe8d85788e01cdd00ff67` |
| `llama-completion.exe` SHA-256 | `4208fb923f6109143d47035aee315295c1c1ce87a062a8cfbbcb02a4d8a3fee2` |
| Build | Clang 20.1.8 for Windows x86_64 |
| License | MIT |
| Git status | ignored and untracked |

The official archive digest was obtained from the GitHub release API and checked before extraction. Phase 5 does
not use llama.cpp RPC or an HTTP inference server.

## Security advisory review

- `GHSA-3p4r-fq3f-q74v` affects llama.cpp through `b8145`; its GGUF heap overflow is fixed in `b8146` and later.
- `GHSA-96jg-mvhq-q7q7` affects pre-`b7437` GGUF parsing and is patched by the later fixed release line.
- The llama.cpp advisory list also contains RPC-specific issues. Phase 5 neither starts nor links the RPC server.
- `b10223` is newer than the reviewed parser fixes. This reduces known-version exposure; it does not make native
  parsing safe against unknown vulnerabilities or replace provenance and checksum verification.

## Distribution status

Neither model weights nor native runtime binaries are tracked, packaged, published, or downloaded by normal CI.
Paths are explicit laboratory inputs until later gates prove a production adapter.
