# Phase 5.3 baseline

Date: 2026-08-22

## Repository checkpoint

| Item | Value |
|---|---|
| Branch | `phase-5-3-evaluation-parity` |
| Phase 5.2 completed commit | `c497ce9c67479115215e28f617a535f50e5f0f22` |
| Annotated local tag | `phase-5-2-complete` |
| Remote push | none; no remote configured |

The completed Phase 5.2 continuation was preserved in three additional local
commits before the tag: qualification harness, execution evidence, and the failed
Gate C documentation. No model or adapter weights are tracked.

## Product and research verification

| Check | Result |
|---|---|
| Java runtime | Amazon Corretto 21.0.12.1 |
| `mvnw.cmd clean verify` | PASS; 11 reactor modules; 01:07 |
| Surefire | 374 tests; 0 failures/errors/skips |
| Failsafe | 11 tests; 0 failures/errors/skips |
| `uv lock --check` | PASS; 64 packages resolved |
| Python compilation | PASS |
| Research unit tests | 10/10 PASS |

The normal Java build loaded no model and required no WSL, CUDA, Python runtime,
or network model operation.

## Model and runtime artifacts

| Artifact | Identity | Bytes / SHA-256 |
|---|---|---|
| Official base | `Qwen/Qwen3.5-2B@15852e8c16360a2fea060d615a32b45270f8a8fc` | checkpoint 4,571,198,095 bytes; manifest `1dc49db3fd0a49ee7bf33190b04c1bac5d1ed489e87855a81c64ac7344571981` |
| SafeTensors shard | official one-shard text/multimodal checkpoint | `aa33250c4fc64891ddfaba3a314fd9542ea371843c387178b425fbcc5ed680b1` |
| Selected adapter | F1, rank 8 | directory SHA-256 `6642b7e3bad7eb64c6199d56db60935aabda54b5a2c26f33af4e79bc8d5ab6ca` |
| Prior GGUF | third-party Q4_K_M | 1,396,198,496 bytes; `57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8` |
| llama.cpp | official `b10223`, commit `11924d4c17abc27383376a1ac6a24fa3e36c1c0c` | executable `4208fb923f6109143d47035aee315295c1c1ce87a062a8cfbbcb02a4d8a3fee2` |

The official model and F1 adapter are in the WSL cache. The GGUF and native
runtime are under the ignored repository-local `.stackmate` laboratory directory.
All four hashes were recalculated at Phase 5.3 start.

## Frozen prompt and corpus inputs

| Input | Version / SHA-256 |
|---|---|
| V2 manifest | `292b395f6024837a52878b38a6c0170a62cb29fdfcc89df68b845ced7732f294` |
| Prompt split manifest | `147d9a9e7e312d02abbf113ed3fd255bd4a3ee7841413c488f36deb44c016a07` |
| Prompt manifest | `1cd31f9a244f69d9e56c808a51a20ed621ed32785b00b582e4e5fe27bec0b474` |
| P0 semantic prompt | `phase5-schema-v2`; `3362afedc99b7c930a8423a33d011bb8b8a026d7072befef6041f6629b36fa03` |
| P3 semantic prompt | `phase5.1-p3-v1`; `d5117da41bd8af64982035fb75dbac91968249ea48fd02f120c532755074bf7f` |
| Output schema | version 2 |
| Validator | repository `DefaultInferenceOutputValidator` at the baseline commit |

The parity source is exactly the 40 IDs in `promptDevelopment`. Four require
restraint. Holdout, blind, and post-training challenge cases are excluded.

## Historical measurements to reproduce

The prior Q4_K_M llama.cpp development result was P0: 38/40 valid, 39/40
cause/check; P3: 40/40 valid, 36/40 cause/check, with all 4/4 development
restraint cases passing. These are historical controls, not Phase 5.3 parity
results. New parity reports must retain raw prompt, template, token, generation,
extraction, validator, and latency evidence.

Phase 5.2 locked Transformers results remain historical exposed diagnostic data:
B0 was 1/70 cause/check, B1 was 22/70 cause and 26/70 check, and selected A1 was
44/70 cause and 45/70 check with 19 rejected unknown-ID generations.
