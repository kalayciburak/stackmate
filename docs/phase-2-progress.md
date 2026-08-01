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

## Phase 2 - packaged-process end-to-end verification

Status: completed on Windows Java 21

### Completed work

- Added the dedicated `stackmate-e2e` module and documented the boundary in ADR 0010.
- Added 11 real Failsafe integration tests. They start the repackaged sample JAR with `ProcessBuilder`, use random local
  ports, poll bounded readiness, send real HTTP requests, capture both process streams, request graceful shutdown, and
  assert that every observed child PID has exited.
- Covered normal, database duplicate, generic Java, malformed JSON, redaction, repeated, disabled, and Turkish modes.
- Verified the documented capture limit: Spring MVC handles malformed JSON internally with HTTP 400, so the servlet
  escape filter does not claim a diagnosis.
- Added a deterministic sample-only child using the production length-prefixed standard-I/O protocol and managed
  lifecycle. Normal CI covers valid output, unknown evidence, fabricated source, invalid JSON, oversized framing,
  invalid UTF-8, timeout, exit code 17, queue saturation, private child stderr, and shutdown during inference.
- Added an explicit `real-model-e2e` profile. It is excluded from normal Failsafe execution and requires a user-supplied
  model path.

### Verification

| Command | Result |
|---|---|
| First `./mvnw.cmd -pl stackmate-e2e -am verify` | Failed during discovery: non-static lifecycle methods lacked `@TestInstance(PER_CLASS)` |
| `./mvnw.cmd -rf :stackmate-e2e verify` | Did not run: resume could not resolve the uninstalled sample artifact outside its reactor segment |
| Final `./mvnw.cmd -pl stackmate-e2e -am verify` | Success in 47.743 s; 11 Failsafe tests passed in 29.700 s; no failures/errors/skips |
| Warning-clean repeat of the same command | Success in 47.450 s; the same 11 Failsafe tests passed in 29.626 s |

The successful run also executed the upstream unit suites and Maven Enforcer rules. HTTP status and Boot error bodies
were unchanged in all asserted modes. The fake child's stderr token/ANSI payload never appeared in parent output, and
all observed descendant processes were dead after graceful application shutdown.

### Platform status

- Windows x86_64, Amazon Corretto 21.0.12: executed and passed.
- Linux and macOS: not executed locally; workflow results must not be claimed until remote CI runs.

### Next phase

Freeze and commit benchmark corpus V2 before changing analyzer behavior or the inference contract.

## Phase 3 - independent benchmark corpus V2 freeze

Status: completed; deterministic evaluation is next

### Completed work

- Froze 80 independently authored cases at commit `7874415c64b1922fca7b8d54695601dba26744f8` with manifest SHA-256
  `292B395F6024837A52878B38A6C0170A62CB29FDFCC89DF68B845CED7732F294`.
- Fixed the split at 60 development and 20 holdout cases.
- Classified 54 cases as real executable recipes and 26 as synthetic, giving 67.5% real runtime/integration coverage.
- Covered the required eight domains with the planned 10/12/12/20/8/8/5/5 distribution.
- Kept ground-truth category/cause/check identifiers independent of production analyzer constants and added explicit
  unknown-error restraint cases.
- Added strict manifest loading and executable Java, Spring, MVC, validation, H2, security, network/TLS, concurrency,
  and adversarial recipes.
- Recorded the design in ADR 0011 and `docs/benchmark-v2-corpus.md`. Historical V1 evidence remains in
  `docs/benchmark.md`.

### Verification

| Command | Result |
|---|---|
| First full benchmark-module test | Failed compilation: Spring 7 placeholder helper signature and a checked sleep lambda required fixture corrections |
| Second full benchmark-module test | Ran 54 real recipes; 8 failures exposed six wrapper/root ground-truth mistakes plus invalid H2-lock and HTTP fixtures |
| First focused corpus test | 55/56 passed; H2 lock's deepest cause was `MVStoreException`, not its `JdbcSQLTimeoutException` wrapper |
| Final focused corpus test | Success in 6.994 s; 56 tests, 0 failures/errors/skips |
| `Get-FileHash -Algorithm SHA256 .../manifest.json` | `292B395F6024837A52878B38A6C0170A62CB29FDFCC89DF68B845CED7732F294` |

The failed runs are recorded because they demonstrate that expected values were not generated from production output.
No production analyzer or inference-contract code changed before the freeze commit.

### Remaining risks

- Real execution here means repeatable local framework/driver recipes, not a claim that all cases came from an external
  production application.
- H2 reproduces JDBC behavior but does not substitute for PostgreSQL/MySQL driver coverage.
- Linux and macOS execution remains unverified until CI runs.

### Next phase

Evaluate the unchanged deterministic pipeline against frozen V2, report development and holdout results separately,
then classify failures before changing any analyzer behavior.

## Phase 3 - unchanged deterministic evaluation on corpus V2

Status: completed

### Actual result

- Category accuracy: 59/80 (73.75%); development 43/60 (71.67%), holdout 16/20 (80.00%).
- Root type: 80/80; controlled application-frame retention: 80/80.
- Annotated fact precision: 27/30 (90.00%); recall: 27/46 (58.70%).
- Unknown restraint: 8/8; false certainty: 0.
- Sanitization failures and secret leakages: 0.
- Fingerprint stability: 80/80, including volatile UUID/numeric values; two compatible cross-case collision groups and
  zero conflicting-category groups.
- Warm in-test observation: median 66 microseconds, P95 218 microseconds across 2,000 samples. This is not a JMH claim.

The 32 non-passing cases classified as 14 incorrect classifications, 7 unsupported categories, and 11 missing evidence
extractions. Detailed case output and metric semantics are in `docs/benchmark-v2-results.md`.

### Verification

| Command | Result |
|---|---|
| First focused evaluation compile | Failed: a lambda parameter shadowed the actual-category local |
| Final focused evaluation | Success in 8.693 s; reports written, safety invariants passed |

### Next phase

Redesign inference schema and prompts so the local model can only explain and rank deterministic facts. Then add the
evidence-aware policy; deterministic V2 gaps remain visible and will not be hidden by model output.

## Phase 4 - reduced model authority and evidence-aware triggering

Status: completed

### Completed work

- Replaced the experimental inference contract with schema version 2. Category, evidence strength, facts, candidate
  causes/checks, and application locations are deterministic inputs; the model can return only bounded prose,
  limitations, and selections of supplied IDs.
- Removed model-authored `StructuredCause`, `StructuredCheck`, and `SourceLocation` output types. Added a migration note
  and ADR 0012 because this intentionally breaks the pre-release experimental SPI.
- Added strict request-scoped validation for schema/language, unknown and unsupported IDs, source-like references,
  technical identifiers, secrets, terminal controls, Markdown fences, destructive commands, unsupported claims, and
  output bounds.
- Added separate natural English and Turkish versioned system and repair prompt resources. Incident and previous-output
  content remain explicitly delimited and normalized as untrusted data.
- Added `NEVER`, `ALWAYS`, and `WHEN_ENHANCEMENT_IS_USEFUL` trigger policies. The default runs only for non-fallback
  `MEDIUM` evidence, before executor submission; `HIGH`, `LOW`, `UNKNOWN`, and fallback diagnoses skip inference.
- Isolated H2 benchmark recipes with unique per-execution database names after a repeated module run exposed shared
  in-memory state between the evaluation and recipe suites. The frozen manifest and its hash did not change.

### Verification so far

| Command | Result |
|---|---|
| Clean affected test compilation | Success after updating stale schema-V1 tests |
| Inference API tests | 27 passed, 0 failures/errors/skips |
| JVM inference tests | 26 passed, 0 failures/errors/skips |
| Spring policy/configuration focused tests | 13 passed, 0 failures/errors/skips |
| Benchmark module plus dependencies | Success in 18.256 s; benchmark module 62 tests passed |
| Packaged schema-V2 fake-child verification | Success in 46.469 s; 11 Failsafe tests passed in 29.215 s |

The first repeated benchmark-module run failed six H2 cases because `DB_CLOSE_DELAY=-1` preserved databases with names
derived only from recipe IDs. A monotonic execution suffix now makes the test recipes independent of suite order and
repeat execution.

### Next phase

Continue with focused deterministic hardening based on the frozen V2 failure classifications, then rerun V2 without
changing its ground truth.

## Phase 4b - post-freeze deterministic hardening

Status: completed

### Result

- Category accuracy improved from 59/80 to 74/80; development improved from 43/60 to 55/60 and holdout from 16/20 to
  19/20.
- Added contextual analyzer precedence, root-aware JDBC wrapper analysis, concrete array/string index rules, both
  Jackson package generations, optimistic-lock persistence-state classification, JDK unresolved-address handling, and
  bounded typed property/type/request/handler evidence.
- Increased analyzer versions for every changed rule set so diagnostic fingerprints cannot silently reuse stale
  diagnoses.
- Preserved 8/8 unknown restraint, 0 secret leaks, 0 sanitization failures, 80/80 fingerprint stability, and zero
  conflicting-category collision groups.
- Retained six category disagreements and documented bad/unevaluable ground truth instead of adding corpus-specific
  rules.

### Verification

| Command | Result |
|---|---|
| Core focused verification | 101 tests passed |
| Affected reactor plus benchmark | 315 tests passed in 21.617 s |
| Frozen V2 evaluation | 74/80 category, 80/80 root, 31/46 fact recall, 8/8 restraint |

### Next phase

Harden the managed-child protocol, model paths, sanitizer, and process lifecycle with the remaining adversarial cases,
then run the reduced-contract local-model lab.

## Phase 5 - adversarial boundary hardening

Status: completed

### Completed work

- Closed truncated-JWT and zero-width credential-key sanitizer bypasses without increasing untrusted input bounds.
- Strengthened SafeTensors pre-validation with known dtype widths, exact shape-to-byte checks, non-overlap, no-gap, and
  full-data indexing requirements.
- Verified relative and Unicode model paths and rejected symbolic-link model roots on this Windows filesystem.
- Added empty/negative/missing frame coverage and proved that mismatched protocol versions and unsolicited multiple
  child responses fail closed and terminate the child.
- Reused existing tests for malformed UTF-8, duplicate/deep/unknown JSON, truncated/oversized frames, timeout,
  interruption, bounded restart, queue saturation, and packaged orphan cleanup rather than duplicating them.

### Verification

```text
./mvnw.cmd -pl stackmate-inference-jvm -am spotless:apply test
```

Result: success in 8.361 s; core 102, inference API 27, inference JVM 30; 159 tests total, zero failures, errors, or
skips. Symbolic-link creation and rejection executed successfully rather than being skipped.

### Remaining risks

- Model pre-validation reduces malformed metadata exposure but does not sandbox Jlama or limit child RSS at OS level.
- Targeted adversarial cases are not a substitute for long-running property-based fuzzing.
- Packaged fake-child E2E will run again in the final clean verification.

### Next phase

Run model Lab V2 using the schema-V2 explanation-only contract, beginning with the existing local Qwen model and
screening no more than two documented candidates.
