# Phase 5.1 baseline reproduction

Date: 2026-08-02

## Repository and environment

| Item | Reproduced value |
|---|---|
| Reviewed Phase 5 commit | `d29d789933a6394db942435e2c5ef607cda53f2f` |
| Local tag | `phase-5-complete` |
| Phase 5.1 branch | `phase-5-1-prompt-alignment` |
| Initial tree | clean |
| Remote | none configured |
| OS / hardware | Windows 11 Pro 10.0.22631; Ryzen 9 7950X; 64 GB RAM |
| Java | Amazon Corretto 21.0.12 |
| Maven | wrapper 3.9.16 |

The shell initially resolved Java 17 and an invalid `JAVA_HOME`; that command
stopped before Maven ran. The authoritative commands set only the command-local
`JAVA_HOME` to `<jdk-21-home>`.

## Normal verification

| Measurement | Expected | Reproduced |
|---|---:|---:|
| Reactor projects | 11 | 11 |
| Surefire | 332 | 332 |
| Failsafe | 11 | 11 |
| Total | 343 | 343 |
| Failures / errors / skips | 0 / 0 / 0 | 0 / 0 / 0 |
| Workspace workers after run | 0 | 0 |

`clean verify` completed successfully in 01:01 Maven time. The run used the
repository's check-only Spotless binding; no formatter apply command was run.

## Pinned P0 reproduction

The existing Phase 5 prompt was run without source changes against the same 80 V2
and 20 blind cases. Model, GGUF, runtime, hashes, schema, validator, context 2,048,
192-token output limit, seed 42, greedy profile, timeout, and one-process-per-case
execution were unchanged.

| Metric | Phase 5 | Reproduced |
|---|---:|---:|
| Valid structured output | 100/100 | 100/100 |
| Cause accuracy | 94/100 | 94/100 |
| Check accuracy | 94/100 | 94/100 |
| V2 development cause/check | 56/60 / 56/60 | 56/60 / 56/60 |
| V2 holdout cause/check | 19/20 / 19/20 | 19/20 / 19/20 |
| Blind cause/check | 19/20 / 19/20 | 19/20 / 19/20 |
| Strict restraint | 1/11 | 1/11 |
| Missing explicit limitation | 10 | 10 |
| Generic fallback misuse | 4 | 4 |
| Repair / timeout / crash | 0 / 0 / 0 | 0 / 0 / 0 |
| Unknown IDs / fabricated refs | 0 / 0 | 0 / 0 |
| Leakage / injection / hidden thinking | 0 / 0 / 0 | 0 / 0 / 0 |
| Process median / P95 | 5,109 / 6,031 ms | 5,142 / 6,141 ms |

The timing shift is machine noise; semantic and selection outputs reproduced. The
canonical case/output-content digest is
`8e23250330feec1b0a097d46bc99069295ac402eb5be86d6b00622591170580c`.

## Unsupported-claim reproduction

The current automatic report says zero accepted unsupported claims because its
detector recognizes only validator issue codes and case `forbiddenClaims`. Manual
review reproduced the same three accepted claims:

| Case | Reproduced unsupported alternative |
|---|---|
| `DB-POOL-TIMEOUT-017` | driver issues |
| `B5-CIRCULAR-DEPENDENCY-ROUTER` | missing bean |
| `B5-DB-FOREIGN-KEY-REGION` | data-type mismatch or invalid value |

Therefore the authoritative baseline remains three manually accepted unsupported
claims, not the incomplete automatic zero. Phase 5 reports were not changed.

Sanitized command evidence is stored in [`baseline/`](baseline/). Generated raw
machine reports remain ignored under `stackmate-benchmark/target/`.
