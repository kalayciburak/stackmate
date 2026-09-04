# Phase 6 baseline

```text
Starting branch        phase-5-5-3-restricted-logit-binary-lora
Starting HEAD          629f24001cc7fbcb5f36e8df2fe4f96c1ef39d87
Completion tag         phase-5-5-3-complete
New branch             phase-6-public-alpha-hardening
Phase 6 baseline       c14cbfe270f8520d379712209afd46fc8e4d5f1d
Working tree           clean before Phase 6 changes
Remote                 origin configured
Push/publication       not performed
```

## Toolchain

```text
Operating system       Windows 11 10.0 amd64
Java                   Amazon Corretto 21.0.12.1
Maven                  3.9.16
Spring Boot            4.1.0
Spring Framework       7.0.8
Reactor modules        11
Build duration         1 minute 29 seconds
```

The successful Windows build used the existing JDK through a process-local
`JAVA_HOME`. The first sandboxed attempt could not start the wrapper or WSL;
the same commands were then run with explicit sandbox approval. No dependency,
package or global environment setting was changed.

## Baseline verification

```text
Surefire               517 passed
Failsafe               11 passed
Failures/errors/skips  0/0/0
Python research tests  63/63 passed
Dependency convergence PASS
Maven Enforcer         PASS
Spotless check         PASS
Core coverage          96% line / 87% branch
Auto-config coverage   79% line / 59% branch
Tracked model files    0
```

Research verification remains a separate WSL command and is not required by
the normal product build.

These counts are the accepted pre-hardening baseline, not the final RC result.
The completed Phase 6 tree later passed 543 Surefire and 5 Failsafe tests with
zero failures, errors, or skips. That final evidence is recorded separately in
`progress.md` and `reproducibility.md`; it does not replace this historical
baseline.

## Reactor inventory

| Module | Baseline role |
|---|---|
| `stackmate-parent` | parent/build aggregation |
| `stackmate-core` | JDK-only deterministic core |
| `stackmate-inference-api` | historical inference experiment |
| `stackmate-inference-jvm` | historical Jlama experiment |
| `stackmate-spring-boot-autoconfigure` | deterministic plus baseline mixed inference auto-configuration |
| `stackmate-spring-boot-autoconfigure-local` | created in Phase 6 as the historical Spring inference boundary |
| `stackmate-spring-boot-starter` | deterministic starter |
| `stackmate-spring-boot-starter-local` | historical local-inference starter |
| `stackmate-sample` | executable sample; the baseline used the local starter |
| `stackmate-e2e` | packaged-process tests |
| `stackmate-startup-baseline` | startup benchmark fixture |
| `stackmate-benchmark` | benchmark and research labs |

Gate A preserved all historical research output. Gate B subsequently separated
the mixed auto-configuration and sample surfaces before RC assembly.
