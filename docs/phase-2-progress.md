# StackMate Phase 2 progress

This log records independently executed Phase 2 work. Historical Phase 1 reports remain unchanged.

## Phase 0 - baseline reproduction

Status: completed

### Repository entry state

- Started: 2026-08-01
- Initial commit: `3e319b1bcef0ab954ab4164a1ed585416bc144e2`
- Initial branch: `main`
- Initial working tree: clean
- Phase 2 branch: `phase-2-hardening`
- Existing history: preserved; no commit was rewritten
- Remote operations: none

### Baseline review

- Read the English and Turkish READMEs, architecture, progress, benchmark, model-selection, privacy/threat-model,
  and all existing ADRs before production changes.
- Confirmed the existing eight child Maven modules and nine-project reactor including the parent.
- Inspected runtime dependency trees for every reactor project. Dependency convergence passed. The core has no
  runtime dependency, the inference API depends only on core, the deterministic starter does not resolve Jlama,
  and the local starter resolves the intended inference runtime.
- Inspected all built StackMate JARs. Starter JARs contain no classes, the autoconfigure JAR contains current Boot
  registration and configuration metadata, and no starter/sample JAR contains model weights.
- Executed deterministic packaged samples in English and Turkish, duplicate, redaction, and disabled modes. The
  HTTP status/body remained the normal Spring Boot response in every failure scenario.
- Executed two bounded real-model requests with the existing Qwen2.5 model. The first database explanation was
  rejected or failed without trusted output; the warm number-format request produced a validated explanation in
  6,702 ms. Inference remained asynchronous: the corresponding HTTP request returned in 115 ms.

### Commands and results

| Command | Result |
|---|---|
| `git status --short` | No output; clean working tree |
| `git rev-parse HEAD` | `3e319b1bcef0ab954ab4164a1ed585416bc144e2` |
| `git switch -c phase-2-hardening` | New local branch created |
| `./mvnw.cmd -Dscope=runtime dependency:tree` | 9-project reactor success; every module inspected |
| `./mvnw.cmd clean verify` | Success; Maven 23.413 s, wall clock 24,629 ms |
| Packaged sample, default profile | HTTP 500 preserved; English deterministic diagnosis rendered |
| Packaged sample, `turkish` | HTTP 500 preserved; Turkish deterministic diagnosis rendered |
| Packaged sample, duplicate request | Second occurrence rendered count 2 |
| Packaged sample, `disabled` | HTTP 500 preserved; zero StackMate diagnostic blocks |
| Packaged sample, `hybrid` | Warm model output validated in 6,702 ms; HTTP returned in 115 ms |

### Reproduced baseline

| Measurement | Phase 2 reproduction |
|---|---:|
| Reactor projects | 9 including parent |
| Child modules | 8 |
| Unit/test-lifecycle tests | 226 |
| Failures / errors / skipped | 0 / 0 / 0 |
| Failsafe integration tests | 0 |
| Core line / branch coverage | 96.36% / 87.58% |
| Inference API line / branch coverage | 91.12% / 75.50% |
| Inference JVM line / branch coverage | 75.44% / 61.36% |
| Spring autoconfigure line / branch coverage | 82.25% / 60.81% |
| Sample line coverage | 93.10% |
| Benchmark line / branch coverage | 30.20% / 13.53% |
| Sample executable JAR | 36,389,017 bytes; no model-weight entry |
| Existing model file | 988,097,824 bytes |
| Existing model SHA-256 | `FDF756FA7FCBE7404D5C60E26BFF1A0C8B8AA1F72CED49E7DD0210FE288FB7FE` |

The reported Phase 1 test and coverage baseline was reproduced exactly. The small model sample also reproduced the
reported qualitative state: valid output is possible, but validity is not reliable and warm latency remains above
the target. This bounded sample is not a replacement for the later V2 model benchmark.

### Decisions

- Reproduce and archive Phase 1 behavior before changing production code.
- Treat Phase 1 benchmark numbers as historical evidence, not current verified results.
- Add a repository-grounded Phase 2 threat model alongside the required privacy document update.

### Remaining risks

- Packaged-process inference behavior is not yet covered by a Failsafe test.
- Phase 1 model measurements remain Windows-only and fail Turkish, latency, and memory targets.
- StackMate redacts its own rendered block, but the intentionally preserved host application logger prints the
  original exception message. Applications must not place secrets in exception messages.

### Next phase

Perform the independent architecture, concurrency, process-protocol, sanitizer, and public-API audit.

## Phase 1 - independent architecture and implementation audit

Status: completed, with the inference-contract redesign explicitly deferred until after corpus V2 is frozen

### Completed work

- Audited public contracts, Maven dependency direction, Boot auto-configuration, executor and deduplication behavior,
  managed-child framing/lifecycle, model-path validation, sanitization, fingerprinting, export, and console boundaries.
- Published the severity-ranked review in `docs/reviews/phase-2-architecture-audit.md` and the repository-grounded
  security analysis in `docs/reviews/stackmate-threat-model.md`.
- Fixed three HIGH findings: raw child stderr inheritance, non-interruptible serialized inference, and incomplete model
  metadata/resource validation.
- Fixed practical MEDIUM findings in strict JSON protocol parsing, whole-document export truncation, sanitizer failure
  fallback, and unsafe configuration bounds.
- Preserved the existing public API and deterministic module architecture. No LOW finding was refactored for aesthetics.

### Verification

| Command | Result |
|---|---|
| `./mvnw.cmd spotless:apply` | Success; only changed Java sources were formatted |
| First affected-module `test` | Failed: one new assertion expected a different bounded capture-failure code |
| Second affected-module `test` | Failed: one property-message assertion used wording different from production |
| Final `./mvnw.cmd -pl stackmate-spring-boot-autoconfigure,stackmate-inference-jvm -am test` | Success; 211 tests, 0 failures/errors/skips after the lazy-wrapper regression is included |
| JDK 21 `jdeps` | Core uses `java.base` and `java.sql`; inference API uses `java.base` and core only |

The two intermediate failures were test expectation errors and are retained here rather than hidden. The complete clean
reactor verification will run again after the packaged E2E module is integrated.

### Remaining risks

- Phase 1 model contract still duplicates deterministic authority until schema version 2 lands.
- Packaged fake-child lifecycle and orphan cleanup are not yet proven by Failsafe.
- Child-JVM isolation is not an OS sandbox; malicious model/native-runtime risk cannot be eliminated in Java code.
- Host application logging can expose the original secret-bearing exception because StackMate intentionally does not
  take ownership of logging appenders.

### Next phase

Add packaged-process Failsafe tests that start the executable sample, exercise real HTTP, use the real local IPC
lifecycle with a deterministic fake child, and prove shutdown cleanup.
