# StackMate Phase 3 progress

This log records Phase 3 work without modifying the historical Phase 1 or Phase 2 reports.

## Phase 0 - Phase 2 baseline reproduction

Status: completed on 2026-08-01

### Repository entry state

- Starting branch: `phase-2-hardening`
- Starting and expected Phase 2 commit: `cf1518c5dde5af940740e0eb35ce7bd808caaa64`
- Starting working tree: clean
- Local annotated tag: `phase-2-complete`, pointing to the expected commit
- Phase 3 branch: `phase-3-quantized-local-inference`
- Configured Git remotes: none
- Remote operations: none

### Completed work

- Read both READMEs, the changelog, Phase 2 progress and benchmark results, model selection, privacy/threat model,
  architecture audit, and all 14 existing ADRs before changing production code.
- Rebuilt and verified all 11 reactor projects with Java 21.
- Recounted Surefire and Failsafe XML reports, coverage, dependency direction, artifact contents, and worker cleanup.
- Executed the packaged deterministic database-duplicate sample in English and Turkish on random local ports.
- Confirmed that the normal HTTP 500 response body was preserved and that no inference child remained afterward.

### Commands and results

| Command | Actual result |
|---|---|
| `git status --short` | No output; clean entry tree |
| `git rev-parse HEAD` | `cf1518c5dde5af940740e0eb35ce7bd808caaa64` |
| `git tag -a phase-2-complete ...` | Local annotated tag created at the Phase 2 commit |
| `git switch -c phase-3-quantized-local-inference` | Local branch created; no push |
| `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` | Success; Maven 1:00, measured wall 61,278 ms |
| Runtime dependency tree for core/API/starters | Success; deterministic starter contains no inference runtime |
| Packaged English sample | HTTP 500 and standard Boot JSON body preserved; deterministic card present |
| Packaged Turkish sample | HTTP 500 and standard Boot JSON body preserved; Turkish deterministic card present |

### Reproduced test and coverage baseline

| Measurement | Expected Phase 2 | Reproduced Phase 3 entry |
|---|---:|---:|
| Reactor projects | 11 | 11 |
| Surefire tests | 314 | 314 |
| Failsafe tests | 12 | 12 |
| Failures / errors / skipped | 0 / 0 / 0 | 0 / 0 / 0 |
| Core line / branch | 96.46% / 87.14% | 96.46% / 87.14% |
| Inference API line / branch | 91.25% / 75.27% | 91.25% / 75.27% |
| Inference JVM line / branch | 80.50% / 63.71% | 80.50% / 63.71% |
| Spring autoconfigure line / branch | 83.19% / 62.79% | 83.19% / 62.79% |

No built JAR contained a `.gguf`, `.safetensors`, or `.onnx` entry. No Jlama or StackMate fake inference worker was
running after verification and the sample processes.

### Remaining work

Research the exact Qwen3.5 model revisions, GGUF provenance, current llama.cpp support/security floor, and the
java-llama.cpp embedded revision before adding an isolated screening path.

## Phase 1 - Model and runtime research

Status: completed on 2026-08-01

### Completed work

- Pinned immutable official Qwen3.5-2B and Qwen3.5-4B revisions and confirmed their Apache-2.0 licenses.
- Confirmed that the official repositories do not publish the required Q4_K_M GGUF artifact.
- Selected two pinned Bartowski Q4_K_M files strictly as feasibility inputs and recorded expected sizes and hashes.
- Checked available disk space before model download; approximately 554 GiB was free.
- Selected official llama.cpp release `b10219` at commit `c629da565c80b0b17fac6262acdca4d772e745d8`.
- Downloaded its official Windows x86_64 CPU archive into the ignored lab directory, verified SHA-256, and confirmed
  the executable version.
- Reviewed official llama.cpp security advisories. The chosen release is above the relevant GGUF parser fixes. The
  unused RPC backend remains an explicitly excluded upstream risk.
- Rejected published java-llama.cpp binaries because their embedded llama.cpp `b4916` revision is too old for Qwen3.5
  and the selected security floor.

### Commands and results

```text
llama-cli --version
=> version 10219 (c629da565), Clang 20.1.8, Windows x86_64

Get-FileHash llama-b10219-bin-win-cpu-x64.zip -Algorithm SHA256
=> 5F3FC78E61D7402F7051C3580159C8A12FF6CB98912E42F4272932E1AFB7F882

Get-PSDrive C
=> approximately 554 GiB free before model download
```

### Remaining risks

- The experimental GGUF cards do not prove the exact immutable official upstream revision used during conversion.
- Only Windows x86_64 has been executed.
- Native GGUF parsing remains memory-unsafe code even with a patched, pinned runtime.
- Gate A model quality is not yet known.

### Next phase

Add an isolated Benchmark V2 llama.cpp screening harness that reuses the Phase 2 prompt, schema, sanitizer, and
semantic output validator without changing Spring production behavior.

## Phase 2 - Isolated llama.cpp screening harness

Status: completed on 2026-08-01

### Completed work

- Added a test-scoped screening lab; no publishable module or Spring production code
  was changed.
- Reused Benchmark V2 cases, Phase 2 prompts, sanitizer, schema-v2 request, and
  semantic validator.
- Added per-request strict JSON schemas whose ID enums come only from supplied
  candidates and evidence.
- Rejected hidden thinking, oversized output, unsupported references, process
  timeout, and nonzero exit as untrusted results.
- Preserved invalid raw output only inside the process boundary; reports contain
  validated prose or bounded issue codes.

### Verification

- `mvnw ... -pl stackmate-benchmark -am test`: passed.
- `mvnw ... spotless:check`: passed.
- Normal Surefire discovery does not execute the real-model lab; it requires an
  explicit class selection and local paths.

## Phase 3 - Gate A candidate screening

Status: completed with **Gate A failed** on 2026-08-01

### Actual result

| Candidate | Strongest relevant result | Peak worker | Decision |
|---|---|---:|---|
| Qwen3.5-2B Q4_K_M | Greedy EN valid/cause/check 10/10; TR 10/10, 6/10, 7/10 | 2,041,524,224 bytes | `NOT_VIABLE` |
| Qwen3.5-4B Q4_K_M | All outputs valid; best cause/check 8/10, 8/10 | 4,510,056,448 bytes | `NOT_VIABLE` |

Across both candidates, accepted unknown IDs, fabricated source references, secret
leakage, prompt-injection success, and hidden thinking were zero. Two 2B recommended
outputs were rejected and quarantined for unsupported references. No timeout, crash,
or orphan process occurred.

The full 80-case model benchmark was not run because no candidate passed screening.
See `model-screening-results.md` for profile timing, manual language review, and
limitations.

## Gates B and C

Status: `NOT_RUN`

Gate A did not authorize a production llama.cpp worker or provisioning system. The
Maven module tree, public API, local starter behavior, runtime network policy, and
deterministic product remain unchanged.

## Final phase work

Status: in progress

- Update public documentation, model selection, threat model, troubleshooting,
  notices, and changelog.
- Run the complete Java 21 Maven verification.
- Confirm test counts, artifact contents, ignored model files, worker cleanup, and
  clean Git state.
