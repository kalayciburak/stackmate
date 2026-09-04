# Third-party notices

StackMate is licensed under Apache License 2.0. Maven dependencies are resolved separately and are not model
weights or source code embedded in this repository.

## Production dependency policy

| Component | Module/scope | License | Notes |
|---|---|---|---|
| Spring Boot 4.1.0 and Spring Framework 7.0.8 | Spring adapter and sample | Apache-2.0 | Framework adapter only; absent from `stackmate-core`. |
| SLF4J | Spring adapter | MIT | Uses the logging facade already present in Spring applications. |
| Jackson | JVM inference adapter | Apache-2.0 | Used for bounded local IPC and model output parsing; absent from core. |
| Jlama 0.8.4 | Local starter only | Apache-2.0 | Experimental local CPU inference; model weights are not included. |

Jlama's optional module brings its Maven-declared transitive libraries only into the local starter, including
Guava, JCTools, Jackson, and Jinjava. They are absent from `stackmate-core` and the deterministic starter. Release
work must refresh the dependency tree, license inventory, and vulnerability review rather than treating this
development-time notice as a final SBOM.

## Model

No model is distributed by StackMate. The evaluated user-supplied model is the official
`Qwen/Qwen2.5-0.5B-Instruct` SafeTensors repository, whose model card declares Apache License 2.0. Commercial use
and redistribution are permitted by that license subject to its notice, patent, and trademark terms; StackMate
does not grant rights in training data, user inputs, or generated outputs.

- Source: https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct
- Format/quantization: official SafeTensors files; no file conversion or additional quantization was applied.
  Jlama reported BF16 model storage, F32 working memory, and BF16 quantized working memory for the executed run
- Evaluated `model.safetensors` size: 988,097,824 bytes
- Evaluated `model.safetensors` SHA-256: `FDF756FA7FCBE7404D5C60E26BFF1A0C8B8AA1F72CED49E7DD0210FE288FB7FE`
- Redistribution by StackMate: none; users provision files explicitly
- Remote code: not enabled or required

Users are responsible for reviewing the license and provenance of any supplied model. StackMate never enables
remote-code execution while loading a model.

Phase 2 also evaluated, but did not distribute, `Qwen/Qwen2.5-Coder-0.5B-Instruct` revision
`ea3f2471cf1b1f0db85067f1ef93848e38e88c25`, whose model card and included license declare Apache License 2.0.
The official BF16 SafeTensors weight was 988,097,824 bytes with SHA-256
`F9523886352217DED3AEEEF552B381AF79D568C6D49A4B9E423288CEA56B0A44`. No evaluated model passed Phase 2 readiness
screening and StackMate recommends neither model for product use.

Phase 3 evaluated two additional Apache-2.0 upstream models through ignored,
third-party Q4_K_M feasibility artifacts:

| Upstream model and revision | Experimental GGUF source revision | Bytes | Verified SHA-256 |
|---|---|---:|---|
| `Qwen/Qwen3.5-2B` `15852e8c16360a2fea060d615a32b45270f8a8fc` | `bartowski/Qwen_Qwen3.5-2B-GGUF` `7d26695454df6de5fbcce2e58681e62dae06ce43` | 1,396,198,496 | `57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8` |
| `Qwen/Qwen3.5-4B` `851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a` | `bartowski/Qwen_Qwen3.5-4B-GGUF` `4168f45a16a1290d65a4ec0fa312ae917a4c15d6` | 3,013,027,808 | `13c16f426047e2de38cd075bdade4a7bcbc8c774384876f677740cda65f8a983` |

The GGUF cards do not cryptographically prove the exact immutable official upstream
revision used during conversion. These files are not production catalog artifacts,
are not distributed or tracked by StackMate, and passed no product-readiness gate.

The Phase 3 lab used the Apache-2.0 official llama.cpp release `b10219`, commit
`c629da565c80b0b17fac6262acdca4d772e745d8`. The verified official Windows CPU x64
archive SHA-256 was
`5f3fc78e61d7402f7051c3580159c8a12ff6cb98912e42f4272932e1afb7f882`.
The executable and its native libraries remain ignored research inputs; none is
packaged or made a production dependency. The experiment did not use the RPC backend.

## Phase 4 research artifacts

Phase 4 evaluated three additional Apache-2.0 upstream models and reused the Phase 3
Qwen3.5-2B control. No model or native file is distributed, tracked, or packaged:

| Upstream model and revision | Experimental GGUF source revision | Bytes | Verified SHA-256 |
|---|---|---:|---|
| `HuggingFaceTB/SmolLM2-360M-Instruct` `a10cc1512eabd3dde888204e902eca88bddb4951` | `bartowski/SmolLM2-360M-Instruct-GGUF` `7be6f65f1db715fe5dc5a4634c0d459b4eed42ec` | 270,590,880 | `2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2` |
| `Qwen/Qwen3-0.6B` `c1899de289a04d12100db370d81485cdf75e47ca` | `bartowski/Qwen_Qwen3-0.6B-GGUF` `60b85c0e3d8fe0f6474f406922a26d12aca4550d` | 484,220,320 | `9acfc1e001311f34b4252001b626f2e466d592a42065f66571bff3790d4e1b14` |
| `Qwen/Qwen2.5-1.5B-Instruct` `989aa7980e4cf806f80c7fef2b1adb7bc71aa306` | `Qwen/Qwen2.5-1.5B-Instruct-GGUF` `91cad51170dc346986eccefdc2dd33a9da36ead9` | 1,117,320,736 | `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e` |

The two Bartowski GGUFs are third-party feasibility artifacts and are not
cryptographically proven conversions of the pinned official revisions. The official
upstream licenses are Apache-2.0. Phase 4 used the Apache-2.0 official llama.cpp
release `b10221`, commit `815a2a5915f22ce6a760c676389c5dfe8535c08f`.
The verified official Windows CPU x64 archive was 18,352,727 bytes with SHA-256
`61dd281e1c5be8a7ae0b403efd0feee9da126e40605c27df35f9d5bef49caaf9`.
The lab excluded server and RPC paths. None of these artifacts passed into a
production dependency or normal CI.

## Phase 5 qualification artifacts

Phase 5 reused only the Qwen3.5-2B Q4_K_M GGUF identity listed above and did not
distribute or track its bytes. The official upstream model revision is
`15852e8c16360a2fea060d615a32b45270f8a8fc`; the third-party GGUF repository revision
is `7d26695454df6de5fbcce2e58681e62dae06ce43`. The artifact is 1,396,198,496 bytes
with SHA-256 `57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8`.
Both upstream model and llama.cpp are Apache-2.0 licensed.

Qualification used the official llama.cpp Windows CPU x64 release `b10223`, commit
`11924d4c17abc27383376a1ac6a24fa3e36c1c0c`. The official archive is 18,352,859
bytes with SHA-256 `74c1ded0512818d98b51940bf9150e16da8ed79cf0cbe8d85788e01cdd00ff67`;
the executed `llama-completion.exe` SHA-256 is
`4208fb923f6109143d47035aee315295c1c1ce87a062a8cfbbcb02a4d8a3fee2`.
No native or model artifact entered a Maven module, starter JAR, normal CI, or Git.
Gate A failed, so no persistent worker or production llama.cpp dependency was added.

## Phase 5.2 continuation artifacts

The continuation downloaded the official Apache-2.0 Qwen3.5-2B Transformers/
SafeTensors checkpoint from `Qwen/Qwen3.5-2B` at immutable revision
`15852e8c16360a2fea060d615a32b45270f8a8fc` for a local, non-published research
run. It contained 4,571,198,095 bytes across 10 files; the complete file-level
SHA-256 manifest is in `docs/phase-5-2/checkpoint-acquisition.md` and
`research/model-alignment/reports/base-model-manifest.json`. `trust_remote_code`
was false and no model, adapter, merged checkpoint, or fine-tuned GGUF was
distributed or committed.

The isolated research packages remain pinned outside Maven: PyTorch 2.13.0+cu130,
Transformers 5.14.1, PEFT 0.20.0, Accelerate 1.14.0, bitsandbytes 0.50.0, gguf
0.19.0, and uv 0.12.1. No telemetry, experiment tracker, remote inference, or
upload service was used. Gate C failed after the locked comparison, so no merge or
conversion tool was invoked.

## Development and test dependencies

Phase 5.2 uses an isolated, non-product WSL research environment. Its direct
packages are pinned in `research/model-alignment/uv.lock` and are not Maven,
runtime, published, or distributed dependencies:

| Research package | Version | Declared license metadata |
|---|---:|---|
| PyTorch | 2.13.0 | SPDX expression covering Apache-2.0, LLVM exception, BSD, Boost, and MIT components |
| Transformers | 5.14.1 | Apache-2.0 |
| PEFT | 0.20.0 | Apache-2.0 |
| Accelerate | 1.14.0 | Apache-2.0 |
| bitsandbytes | 0.50.0 | MIT |
| gguf Python package | 0.19.0 | llama.cpp project package; review upstream notices before redistribution |
| uv | 0.12.1 | environment tool installed for the WSL user, not distributed |

The resolved PyTorch environment includes NVIDIA CUDA runtime libraries under
their upstream package terms. StackMate distributes none of these files. The
official Qwen base checkpoint was acquired locally for the resumed experiment;
no model, adapter, merged model, or fine-tuned GGUF was distributed, committed,
or published. The original third-party GGUF remained unchanged and was not used
for training.

Phase 5.3 adds the following benchmark test-scope dependencies only:

| Test dependency | Version | License / use |
|---|---:|---|
| PostgreSQL JDBC driver | 42.7.11 | BSD-2-Clause; ephemeral local fixture connections |
| Testcontainers PostgreSQL module | 2.0.5 | MIT; explicit Docker-backed PostgreSQL lab |
| Spring Security Core | 7.1.0 | Apache-2.0; controlled authentication exception fixtures |

The explicit lab also uses the local `postgres:17-alpine` container image. StackMate
does not redistribute that image; its PostgreSQL and Alpine component notices
remain governed by their upstream terms. None of these dependencies is added to a
published runtime module.

Phase 5.4.3 reuses the same test-scoped PostgreSQL JDBC/Testcontainers stack and
pins the local image to
`postgres:17-alpine@sha256:18cfe3ef5e6815560c98237d6216d1e5119702fb0f3894c8785dd58b8bbe5d73`.
It adds no Maven or Python dependency. The official Qwen checkpoint and selected
adapter remain local research artifacts and are not redistributed.

JUnit, AssertJ, JaCoCo, Maven plugins, JMH, and other test/build tooling are not runtime dependencies of the
deterministic starter. Their exact resolved versions and licenses are reviewable through the Maven dependency
tree and generated build reports.

## Phase 5.5 pairwise feasibility

Phase 5.5 adds no third-party dependency and distributes no third-party artifact.
Its tokenizer audit reused the local official Apache-2.0 Qwen3.5-2B checkpoint at
revision `15852e8c16360a2fea060d615a32b45270f8a8fc` with remote code disabled. The
pairwise source audit is Python-standard-library code over already committed
controlled data. No model inference, training, adapter, merge, GGUF conversion,
native runtime, cloud service, telemetry, upload, or Maven runtime change occurred.

One accidental Windows-side `uv run --frozen` invocation downloaded the existing
lock's research packages into the user's local cache. Those files are not part of
StackMate, are not committed or redistributed, and add no product/runtime license
obligation beyond the already documented Phase 5.2 research environment.

## Phase 5.5.1 binary feasibility

Phase 5.5.1 adds no third-party dependency and distributes no third-party
artifact. Its tokenizer-only audit reused the local official Apache-2.0
`Qwen/Qwen3.5-2B` checkpoint at immutable revision
`15852e8c16360a2fea060d615a32b45270f8a8fc`, with remote code and network access
disabled. Dataset validity failed before model loading, inference or training.
No model, adapter, merged checkpoint, GGUF, native runtime or Maven dependency was
added, committed, published or uploaded.
