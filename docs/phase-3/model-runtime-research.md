# Phase 3 model and runtime research

Date: 2026-08-01

This note freezes the inputs for the Phase 3 feasibility experiment. It is not a
production model catalog. The GGUF files below are third-party quantizations and may
be used only by the isolated screening harness.

## Official upstream models

| Candidate | Official repository | Immutable revision | License | Relevant behavior |
|---|---|---|---|---|
| LITE | `Qwen/Qwen3.5-2B` | `15852e8c16360a2fea060d615a32b45270f8a8fc` | Apache-2.0 | Post-trained model; the official card describes non-thinking as its default mode. |
| QUALITY | `Qwen/Qwen3.5-4B` | `851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a` | Apache-2.0 | Post-trained model; thinking is enabled by default and must be explicitly disabled. |

Both official repositories provide SafeTensors, not an official Q4_K_M GGUF
repository. The experiment uses text-only input, no vision projector, a 2,048-token
context, and a one-turn non-thinking request. Model cards:

- <https://huggingface.co/Qwen/Qwen3.5-2B>
- <https://huggingface.co/Qwen/Qwen3.5-4B>

The official Hugging Face API was used to resolve the immutable revisions above. No
gated authentication or remote-code trust is required.

## Feasibility GGUF artifacts

The selected artifacts are experimental quantizations published by Bartowski. The
repository cards record `Qwen/Qwen3.5-*` as the base model and llama.cpp release
`b9222` as the quantizer. Release `b9222` resolves to commit
`9a532ae4bab1b164052ce60a738f78538b421c66`.

| Candidate | Pinned repository revision | Artifact | Expected bytes | Expected SHA-256 |
|---|---|---|---:|---|
| LITE | `7d26695454df6de5fbcce2e58681e62dae06ce43` | `Qwen_Qwen3.5-2B-Q4_K_M.gguf` | 1,396,198,496 | `57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8` |
| QUALITY | `4168f45a16a1290d65a4ec0fa312ae917a4c15d6` | `Qwen_Qwen3.5-4B-Q4_K_M.gguf` | 3,013,027,808 | `13c16f426047e2de38cd075bdade4a7bcbc8c774384876f677740cda65f8a983` |

Sources:

- <https://huggingface.co/bartowski/Qwen_Qwen3.5-2B-GGUF>
- <https://huggingface.co/bartowski/Qwen_Qwen3.5-4B-GGUF>

The two artifacts total 4,409,226,304 bytes, below the five-gigabyte Phase 3
download ceiling. Each artifact is below the per-file ceiling. The machine had
approximately 554 GiB free before the model download.

### Provenance limitation

The third-party cards do not cryptographically prove which immutable official Qwen
revision was converted. These files therefore cannot enter a production `READY`
catalog on this evidence alone. Gate A may establish model/runtime feasibility, but a
later catalog would require a reproducible conversion from pinned official weights or
equally strong first-party provenance.

## Runtime selection for screening

The screening runtime is official llama.cpp release `b10219`:

| Field | Value |
|---|---|
| Release | `b10219` |
| Git commit | `c629da565c80b0b17fac6262acdca4d772e745d8` |
| Windows artifact | `llama-b10219-bin-win-cpu-x64.zip` |
| Expected and verified SHA-256 | `5f3fc78e61d7402f7051c3580159c8a12ff6cb98912e42f4272932e1afb7f882` |
| Artifact size | 18,352,875 bytes |
| Reported compiler | Clang 20.1.8 |
| Actual local target | Windows x86_64 CPU |

The extracted CLI reports:

```text
version: 10219 (c629da565)
built with Clang 20.1.8 for Windows x86_64
```

The current CLI exposes GBNF grammar and JSON-schema constrained decoding, explicit
reasoning control, bounded context/output, one-turn operation, and file-based prompt
input. The official README includes Qwen3.5 usage. Sources:

- <https://github.com/ggml-org/llama.cpp>
- <https://github.com/ggml-org/llama.cpp/releases/tag/b10219>
- <https://github.com/ggml-org/llama.cpp/blob/master/grammars/README.md>

Only Windows x86_64 was executed locally. The release publishes Linux x86_64 and
macOS x86_64/arm64 artifacts, but their behavior remains unverified by Phase 3.

## Security advisory review

The selected release is newer than the following relevant parser fixes:

- `GHSA-3p4r-fq3f-q74v`: affected through `b8145`, fixed in `b8146`; the advisory
  describes a high-severity integer overflow affecting malicious model input.
- `GHSA-96jg-qrm6-6j4m`: fixed in `b7824`.
- Earlier high-severity GGUF/tokenizer advisories fixed by `b5662` and `b5721`.

Advisories: <https://github.com/ggml-org/llama.cpp/security/advisories>

The critical RPC advisory `GHSA-j8rj-gc4r-9x3g` remains material to llama.cpp as a
whole. StackMate's experiment does not build, enable, or connect to the RPC backend,
and does not run a network server. This exclusion reduces exposure but is not a claim
that the upstream codebase is vulnerability-free. Native parsing remains a residual
risk even after revision and checksum pinning.

Before execution, every GGUF must match its pinned byte length and SHA-256. An
existing file is not trusted merely because it has a `.gguf` extension.

## java-llama.cpp decision

The published `kherud/java-llama.cpp` releases `v4.1.0` and `v4.2.0`, and the current
source tree inspected for this experiment, pin llama.cpp `b4916`. That revision
predates both Qwen3.5 support and the security floor above. The Maven/native artifact
is rejected for Phase 3; it will not be loaded or embedded. Source:
<https://github.com/kherud/java-llama.cpp>

## Local build constraints

No C/C++ build toolchain (`cmake`, MSVC, GCC, Clang, or Ninja) was available on the
machine. A reproducible local GGUF conversion or native build was not performed. The
official, checksum-pinned llama.cpp binary and pinned third-party quantizations are
acceptable only for the isolated feasibility gate.

## Screening decision

Proceed with Qwen3.5-2B first. The harness must:

- reuse Benchmark V2 incidents and the Phase 2 schema-v2 request, prompts, sanitizer,
  and semantic validator;
- use strict JSON-schema decoding and still perform semantic validation;
- pass prompt data by file rather than command-line argument;
- run with reasoning explicitly disabled and reject any hidden-thinking marker;
- test only the bounded greedy and recommended non-thinking parameter profiles;
- verify the GGUF checksum and size before invoking native code.

Qwen3.5-4B will be screened with the same cases after the 2B result is recorded. No
Spring integration or provisioning work is authorized by this research decision.
