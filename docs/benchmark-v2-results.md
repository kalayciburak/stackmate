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

