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

