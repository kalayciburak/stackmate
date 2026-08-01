# Phase 5 baseline reproduction

Date: 2026-08-02

## Environment

| Item | Reproduced value |
|---|---|
| OS | Microsoft Windows 11 Pro 10.0.22631, x86_64 |
| CPU | AMD Ryzen 9 7950X, 16 cores / 32 logical processors |
| RAM | 67,812,388,864 bytes reported by Windows |
| Java | Amazon Corretto 21.0.12 |
| Maven wrapper | Apache Maven 3.9.16 |
| Spring Boot | 4.1.0 |
| Spring Framework | 7.0.8 |

## Expected versus reproduced

| Phase 4 claim | Expected | Reproduced |
|---|---:|---:|
| Reactor projects | 11 | 11 |
| Surefire tests | 311 | 311 |
| Failsafe tests | 11 | 11 |
| Failures/errors/skips | 0/0/0 | 0/0/0 |
| Core line coverage | 96.46% | 96.46% |
| Core branch coverage | 87.14% | 87.14% |
| Qwen control valid output | 20/20 | 20/20 |
| Qwen control cause selection | 20/20 | 20/20 |
| Qwen control check selection | 20/20 | 20/20 |
| Accepted safety violations | 0 | 0 |

The normal build completed successfully in 57.137 seconds of Maven-reported time. No managed inference worker was
observed after verification.

## Module artifacts after `clean verify`

| Artifact | Bytes |
|---|---:|
| `stackmate-core` | 148,419 |
| `stackmate-inference-api` | 51,500 |
| `stackmate-inference-jvm` | 59,315 |
| `stackmate-spring-boot-autoconfigure` | 104,407 |
| `stackmate-spring-boot-starter` | 1,688 |
| `stackmate-spring-boot-starter-local` | 1,755 |
| `stackmate-sample` executable JAR | 36,393,526 |
| `stackmate-benchmark` | 198,705 |
| `stackmate-startup-baseline` executable JAR | 19,867,631 |

The deterministic starter dependency tree contains Spring auto-configuration, core, Spring Boot/Spring Framework,
and SLF4J only. It does not pull Jlama, Jackson, or any inference implementation. `stackmate-core` has no dependency.

## Packaged behavior

The executable sample was started as an operating-system process on a random port using the `e2e` profile. Requests
to database duplicate, unknown, redaction, and repeated-error endpoints returned Spring Boot's normal HTTP 500 JSON.
StackMate added its deterministic console block without writing the response. The second repeated request logged the
same incident fingerprint with count 2. Under `e2e,disabled`, the null-pointer endpoint still returned HTTP 500 and
the captured process output contained zero StackMate blocks.

Sanitized captures are stored under [`baseline/`](baseline/).

## Phase 4 model control

The existing strict screening lab was run against the frozen Phase 4 corpus with Qwen3.5-2B Q4_K_M, greedy
generation, JSON-schema grammar, and unchanged semantic validation.

| Metric | Reproduced result |
|---|---:|
| Valid structured output | 20/20 |
| Cause selection | 20/20 |
| Check selection | 20/20 |
| Repair attempts | 0 |
| Timeouts / crashes | 0 / 0 |
| Hidden thinking | 0 |
| Prompt-injection successes | 0 |
| Secret leakage | 0 |
| Forbidden claims | 0 |
| Accepted fabricated references | 0 |
| Whole-process median | 4,813 ms |
| Whole-process P95 | 5,577 ms |

This is a control result, not Gate A. It reloads the process/model for every case, reuses a known Phase 4 corpus, and
does not establish persistent-worker behavior.
