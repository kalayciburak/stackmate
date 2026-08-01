# Phase 4 entry baseline

Date: 2026-08-02

## Repository identity

- Starting branch: `phase-3-quantized-local-inference`
- Expected and actual Phase 3 commit:
  `c9e65d174aee24bf104736bb7ffb1f95da8466c4`
- Starting tree: clean
- Local annotated tag: `phase-3-complete`
- Phase 4 branch: `phase-4-english-small-model-tournament`
- Configured remotes: none
- Remote operations: none

## Environment

- Windows 11 Pro 10.0.22631, x86_64
- AMD Ryzen 9 7950X, 16 cores / 32 logical processors
- Approximately 63.15 GiB physical memory
- Amazon Corretto 21.0.12
- Spring Boot 4.1.0 / Spring Framework 7.0.8
- Maven Wrapper 3.9.16

## Normal verification

`mvnw --batch-mode --no-transfer-progress clean verify` passed all 11 reactor
projects in 1:02 Maven time.

| Measurement | Phase 3 expected | Phase 4 reproduced |
|---|---:|---:|
| Surefire tests | 314 | 314 |
| Failsafe tests | 12 | 12 |
| Total | 326 | 326 |
| Failures / errors / skips | 0 / 0 / 0 | 0 / 0 / 0 |
| Remaining workers | 0 | 0 |

The Failsafe suite executed the English and Turkish packaged deterministic modes,
disabled mode, duplicate and redaction scenarios, and the complete packaged fake
child lifecycle. HTTP status/body behavior remained owned by Spring Boot.

## Existing real-model reproduction

The ignored Phase 3 Qwen3.5-2B and llama.cpp artifacts were still available. A
single English greedy 10-case reproduction produced 10/10 valid outputs, 10/10 exact
cause selections, and 10/10 exact check selections. Generation median/P95 was
2,739.39/3,062.57 ms; whole-process median/P95 was 5,439/5,914 ms. This remains a
one-process-per-case measurement, not persistent-worker evidence.

The first Maven invocation for this lab stopped before test execution because an
unquoted PowerShell `-D` option was split into an invalid lifecycle phase. Quoting
each property fixed the command. No model process started during the failed attempt.

## Module and history checks

The reactor contains ten child modules plus the parent. The deterministic starter
still contains no inference runtime. Historical Phase 2/3 benchmark and progress
files were hashed at entry; Gate A work must not modify them.

Sanitized baseline outputs are under `docs/phase-4/baseline/`.
