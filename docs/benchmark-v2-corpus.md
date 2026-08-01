# Benchmark corpus V2

## Frozen identity

| Property | Value |
|---|---|
| Corpus ID | `stackmate-corpus-v2` |
| Schema | `2` |
| Freeze date | 2026-08-01 |
| Freeze commit | `7874415c64b1922fca7b8d54695601dba26744f8` |
| Manifest SHA-256 | `292B395F6024837A52878B38A6C0170A62CB29FDFCC89DF68B845CED7732F294` |
| Cases | 80 |
| Development / holdout | 60 / 20 |
| Real executable / synthetic | 54 / 26 (67.5% / 32.5%) |

This document identifies the immutable V2 input. Evaluation results are written separately and must not change the
manifest. The previous 50-case result remains historical V1 evidence in `docs/benchmark.md`.

## Composition

| Domain | Cases |
|---|---:|
| General Java and wrappers | 10 |
| Spring configuration and dependency injection | 12 |
| MVC, validation and Jackson | 12 |
| JDBC, Hibernate and transactions | 20 |
| Security | 8 |
| Network, TLS, DNS and connections | 8 |
| Concurrency and timeout | 5 |
| Sanitization and adversarial input | 5 |

Provenance is explicit on every case: 28 `REAL_RUNTIME`, 26 `REAL_INTEGRATION`, 19 `SYNTHETIC_WRAPPER`, and 7
`SYNTHETIC_EDGE_CASE`. Real recipes execute JDK failures, Spring bean factories, Spring MVC through MockMvc, Hibernate
Validator, H2 JDBC, Java security, sockets/TLS, and concurrency APIs. Synthetic cases are retained only for wrapper
shapes and edge conditions that are disproportionate or unsafe to reproduce as full infrastructure failures.

## Independence controls

- Expected category strings, cause keys, and check keys were authored in the manifest; they do not import production
  analyzer constants or candidate IDs.
- The fixture code constructs or executes failures directly. It does not invoke analyzer helper methods.
- Production modules cannot depend on benchmark resources because the benchmark is a downstream leaf module.
- Development and holdout splits are fixed in the manifest. Reports must present holdout results separately.
- `RESTRAIN` cases carry no expected cause or check. This makes confident guessing measurable as a failure.
- The corpus was committed before analyzer behavior or inference schema V2 was changed.

## Runtime versions and dependencies

The frozen manifest records Java 21, Spring Boot 4.1.0, Spring Framework 7.0.8, Jackson 2.21.4, and H2 2.4.240. H2 is
test-scoped and solves reproducible JDBC constraint, syntax, conversion, and lock failures without adding a database
driver to a StackMate runtime artifact. Its published POM declares MPL 2.0 and EPL 1.0; it adds no runtime dependency to
consumers.

## Verification and regeneration

Corpus identity:

```powershell
Get-FileHash -Algorithm SHA256 `
  stackmate-benchmark/src/main/resources/corpus-v2/manifest.json
```

Structure and all 54 executable recipes:

```powershell
$env:JAVA_HOME='C:\Users\Burak\.jdks\corretto-21.0.12'
.\mvnw.cmd -pl stackmate-benchmark -am `
  '-Dtest=CorpusV2ManifestTest,CorpusV2RuntimeRecipeTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The freeze verification passed 56 JUnit invocations: two manifest assertions and 54 parameterized runtime recipes.
Changing any case requires a new corpus version and manifest digest; it must not be silently edited to improve a score.

