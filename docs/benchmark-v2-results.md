# Benchmark V2 deterministic results

## Scope and identity

These results evaluate the unchanged Phase 1 deterministic engine against frozen corpus V2. The input is commit
`7874415c64b1922fca7b8d54695601dba26744f8`, manifest SHA-256
`292B395F6024837A52878B38A6C0170A62CB29FDFCC89DF68B845CED7732F294`. Historical V1 numbers remain in
`docs/benchmark.md`; they were not overwritten.

The generated machine-readable and Markdown reports are
`stackmate-benchmark/target/stackmate-benchmark-v2-report.{json,md}`. They are deliberately ignored because timing is
machine-specific. This document is the curated Phase 2 record.

## Results before analyzer changes

| Metric | Result |
|---|---:|
| Cases | 80 |
| Category accuracy | 59/80 (73.75%) |
| Development category accuracy | 43/60 (71.67%) |
| Holdout category accuracy | 16/20 (80.00%) |
| Root-type accuracy | 80/80 (100.00%) |
| Annotated fact precision | 27/30 (90.00%) |
| Annotated fact recall | 27/46 (58.70%) |
| Controlled application-frame retention | 80/80 (100.00%) |
| Unsupported-case restraint | 8/8 (100.00%) |
| False-certainty count | 0 |
| Sanitization failures | 0 |
| Secret leakage | 0 |
| Fingerprint repeat stability | 80/80 (100.00%) |
| Cross-case fingerprint collision groups | 2 |
| Conflicting-category collision groups | 0 |
| Observed warm analysis median | 66 microseconds |
| Observed warm analysis P95 | 218 microseconds |

Fact precision is calculated only within fact types annotated for a case. Evidence of an unannotated type is neither a
false positive nor proof of correctness. Recall excludes the five explicit safety-policy markers, which are evaluated by
the sanitization, injection, and fingerprint checks instead. These rules are encoded in the evaluator, not inferred from
production output.

The latency values are observations from 2,000 warmed in-test samples and are not presented as JMH results. Process
startup and request-thread capture overhead are measured separately later in Phase 2.

## Category accuracy by domain

| Domain | Result |
|---|---:|
| General Java | 9/10 (90.00%) |
| Spring configuration and DI | 8/12 (66.67%) |
| MVC | 7/12 (58.33%) |
| Database | 15/20 (75.00%) |
| Security | 4/8 (50.00%) |
| Network | 6/8 (75.00%) |
| Concurrency | 5/5 (100.00%) |
| Adversarial | 5/5 (100.00%) |

## Failure classification

| Classification | Cases | Meaning |
|---|---:|---|
| Pass | 48 | Category, restraint, and all annotated facts passed |
| Incorrect classification | 14 | A supported-looking but different category won |
| Unsupported category | 7 | The engine honestly returned `UNKNOWN` for an expected supported category |
| Missing evidence extraction | 11 | Category passed but one or more annotated facts were absent |
| False certainty | 0 | No restraint case received a confident diagnosis |
| Sanitization issue | 0 | No controlled secret or terminal escape survived |

Important gaps are wrapper precedence in Spring and MVC, insufficient generic JDK security coverage, H2 lock and
transaction classification, generic JDBC connection cause handling, and typed property/handler/request evidence. These
are not retroactively removed from the corpus. Any fix must be justified as a product capability, tested independently,
and reported as a post-freeze result.

The two collision groups were semantically compatible: two H2 unique-constraint variants and two Spring bean-creation
variants. No fingerprint joined cases with different expected categories. Stability includes incident ID/timestamp
changes for all cases and UUID/numeric-message variation for the explicit volatility case.

## Environment

- OS: Microsoft Windows 11 Pro 10.0.22631, x86_64
- CPU: AMD Ryzen 9 7950X, 16 cores / 32 logical processors
- RAM: 63.16 GiB
- JVM: Amazon Corretto OpenJDK 21.0.12+8-LTS

## Command

```powershell
$env:JAVA_HOME='C:\Users\Burak\.jdks\corretto-21.0.12'
.\mvnw.cmd -pl stackmate-benchmark -am `
  '-Dtest=CorpusV2DeterministicEvaluationTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: build success in 8.693 seconds; one evaluator test passed after executing 80 cases and writing both reports.

## Post-freeze deterministic hardening

No corpus case or expected value changed. General-purpose analyzer changes raised category accuracy while preserving the
holdout split and all safety invariants.

| Metric | Frozen baseline | Hardened result |
|---|---:|---:|
| Category accuracy | 59/80 (73.75%) | 74/80 (92.50%) |
| Development category accuracy | 43/60 (71.67%) | 55/60 (91.67%) |
| Holdout category accuracy | 16/20 (80.00%) | 19/20 (95.00%) |
| Root-type accuracy | 80/80 | 80/80 |
| Annotated fact precision | 27/30 (90.00%) | 31/38 (81.58%) |
| Annotated fact recall | 27/46 (58.70%) | 31/46 (67.39%) |
| Unsupported-case restraint | 8/8 | 8/8 |
| Secret leakage / sanitization failures | 0 / 0 | 0 / 0 |
| Fingerprint stability | 80/80 | 80/80 |
| Conflicting-category collision groups | 0 | 0 |
| Observed warm median | 66 microseconds | 74 microseconds |
| Observed warm P95 | 218 microseconds | 237 microseconds |

The precision denominator grew because the hardened evaluator now scores newly implemented `PROPERTY_NAME`,
`EXPECTED_TYPE`, `HANDLER_METHOD`, and `REQUEST_PATH` evidence rather than silently excluding those types. Seven typed
facts did not equal the frozen expectations. Several are deliberate evidence mismatches: Java reported primitive `int`
rather than the corpus's boxed `Integer`; the executed nested enum class differs from the illustrative package in the
manifest; the synthetic bind message says `billing.retry` while its expected fact says `sample.port`; and the evaluator
does not fabricate HTTP request/handler context for throwable-only recipes.

The remaining six category mismatches are retained rather than overfit:

- malformed JSON and a missing request body are classified as `SERIALIZATION`, not the corpus's broader `WEB_REQUEST`;
- generic `SecurityException` is insufficient to prove authorization, while `SignatureException` and
  `CertificateExpiredException` are not necessarily authentication failures without security context;
- on this Windows run, the TLS-to-plain recipe exposed only `SocketException: An established connection was aborted...`,
  so a TLS category would require an unsupported inference.

H2 2.4.240 also returned SQLState `42S04` for a missing table and `42001` for syntax, while frozen expectations contain
`42S02` and `42000`. The engine preserves the observed driver values; these are recorded as ground-truth defects rather
than rewritten after execution.

The hardened affected-reactor run passed 315 tests (core 101, inference API 27, JVM inference 26, Spring
autoconfiguration 99, benchmark 62) in 21.617 seconds. The generated V2 report observed 2,000 warm samples; these values
remain non-JMH observations.

## Local model Lab V2

The production contract was reduced before screening: the model can explain and rank only supplied facts and IDs.
Category, root type, source locations, and evidence strength are no longer model metrics because they are trusted
deterministic fields. Screening used prompt version 2, a maximum of one repair, a managed child JVM, and 10 cases per
language/candidate.

| Candidate / language | Structured valid | Cause/check exact | Invalid JSON | Median | P95 |
|---|---:|---:|---:|---:|---:|
| Qwen2.5-0.5B-Instruct / English | 0/10 | 0/10 | 10 | 18,808 ms | 23,278 ms |
| Qwen2.5-0.5B-Instruct / Turkish | 0/10 | 0/10 | 10 | 17,103 ms | 27,846 ms |
| Qwen2.5-Coder-0.5B-Instruct / English | 0/10 | 0/10 | 10 | 20,032 ms | 21,779 ms |
| Qwen2.5-Coder-0.5B-Instruct / Turkish | 0/10 | 0/10 | 10 | 21,999 ms | 23,016 ms |

The general-model raw-stack research mode was 0/10 valid, median 30,653 ms and P95 34,352 ms. It is retained only as
a research baseline. The structured input was much smaller and faster but still unusable; input efficiency is not a
substitute for valid output.

Across the four structured screens, accepted fabricated source references, unknown IDs, unsupported claims, secret
leaks, and prompt-injection successes were zero. Invalid-output fallback was 40/40. No valid prose survived, so English
and Turkish readability were not manually scoreable and are not reported as positive results. Both candidates were
eliminated at screening; the full 50-case model run was not performed.

For Qwen2.5-Coder English, the Windows 250 ms process sampler measured a 2,243,895,296-byte worker peak working set and
a 2,426,294,272-byte combined parent-plus-worker approximation. It observed zero process-tree network endpoints. These
measurements do not prove an OS-level network sandbox or cross-platform RSS. The resulting readiness decision is
`NOT_VIABLE`; see ADR 0014.

## Request-thread core path JMH

The representative throwable contains three wrappers/causes, a suppressed exception, SQLState evidence, framework and
application frames. JMH 1.37 used two fresh forks, five one-second warmups and five one-second measurements per fork on
Amazon Corretto 21.0.12.

| Benchmark | Average | 99.9% CI | Min / max iteration |
|---|---:|---:|---:|
| Throwable snapshot | 128.321 microseconds/op | +/- 4.876 | 122.894 / 132.003 |
| Analyzer selection and diagnosis | 32.076 microseconds/op | +/- 1.310 | 31.033 / 33.431 |
| Snapshot, application-frame selection, diagnosis, fingerprint | 212.716 microseconds/op | +/- 4.683 | 209.847 / 219.526 |

This measures the core synchronous work, not servlet dispatch, SLF4J backend cost, or model inference. JMH average-time
results are not P95 latency. They are far below the 5 ms capture and 10 ms deterministic targets on this machine.

## Packaged startup measurement

The process harness packages one minimal WebMVC fixture four ways so application code and endpoint graph stay constant:
without StackMate, deterministic starter, local starter with lazy adapter, and local starter with `lazy-load=false`.
Every observation is a fresh JVM on port zero. Two warmups are excluded and ten starts are measured per mode; readiness
is the Spring `Started ... Application` line. OS file caches are not cleared.

| Mode | Minimum | Median | P95 | Maximum | Median delta vs control |
|---|---:|---:|---:|---:|---:|
| Without StackMate | 1,404 ms | 1,448 ms | 1,467 ms | 1,467 ms | - |
| Deterministic starter | 1,435 ms | 1,485 ms | 1,530 ms | 1,530 ms | +37 ms |
| Local starter, lazy | 1,632 ms | 1,663 ms | 1,714 ms | 1,714 ms | +215 ms |
| Local starter, adapter eager | 1,851 ms | 1,910 ms | 1,944 ms | 1,944 ms | +462 ms |

The eager label is deliberately qualified: `lazy-load=false` validates and initializes adapter metadata during
startup, but Jlama still loads model weights on first inference. No configured model was used in this startup run, so it
is not a weight-loading benchmark.

Environment: Windows 11 10.0.22631, AMD Ryzen 9 7950X, 32 logical processors, 63.16 GiB RAM, Amazon Corretto
21.0.12. Control/deterministic/local executable sizes were 19,867,631 / 20,138,959 / 34,737,977 bytes. The repeatable
script is `stackmate-benchmark/scripts/measure-startup.ps1`; reports under `target/` remain machine-local.
