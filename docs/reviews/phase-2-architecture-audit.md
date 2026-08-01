# Phase 2 architecture and implementation audit

Date: 2026-08-01
Baseline commit: `3e319b1bcef0ab954ab4164a1ed585416bc144e2`
Audit branch: `phase-2-hardening`

## Scope and method

This is an independent review of the Phase 1 implementation, not a restatement of its design documents. The review
covered every production source package, every Maven module and runtime dependency tree, the generated configuration
metadata, public Java types, Spring auto-configuration conditions, concurrency boundaries, the managed-child protocol,
model-path validation, sanitization, export, and the existing test suite. Findings are ranked by plausible impact in a
developer application; optional local inference is treated as untrusted and fail-open.

Evidence was gathered with source inspection, `dependency:tree`, `jdeps`, packaged sample execution, focused JUnit
tests, and the clean baseline `verify`. Locations below are repository-relative.

## Executive result

The deterministic architecture is sound: core remains Spring-independent, the starter boundaries are correct, MVC
capture preserves the servlet response, and the model output passes a separate validation boundary. The audit found no
critical issue. It found three high-risk weaknesses at the local process boundary and four practical medium risks. The
implemented fixes are intentionally narrow; no public API was broken and no low-severity aesthetic refactor was made.

## Findings

### P2-AUD-001 — child stderr bypassed sanitization and output bounds

- Severity: **HIGH**
- Location: `stackmate-inference-jvm/.../ChildProcessLauncher.java`, `ManagedChild.java`
- Evidence: the system launcher configured `ProcessBuilder.Redirect.INHERIT` for stderr. Jlama, malformed model
  metadata, and child exceptions could therefore write directly to the host console.
- Actual risk: secrets, attacker-controlled terminal sequences, large error streams, and local paths could bypass all
  StackMate sanitization. A noisy child could also interfere with console usability.
- Reproduction: start a child that writes a bearer token and an ANSI sequence to stderr; before the fix, output was
  inherited by the application process.
- Proposed fix: pipe and continuously drain stderr into a bounded private tail; sanitize the tail only when producing a
  bounded internal failure reason; never render raw child stderr.
- Fix status: **fixed**. The ring tail is capped at 4,096 characters and process errors are redacted and capped at 400.
- Test added: process stderr piping and protocol error sanitization unit coverage; packaged fake-child coverage is part
  of the Phase 2 E2E suite.

### P2-AUD-002 — serialized inference lock was not interruptible

- Severity: **HIGH**
- Location: `JlamaLocalInferenceEngine.java`, `LazyLocalInferenceEngine.java`, `InferenceProperties.java`
- Evidence: `infer` synchronized on the engine for the full generation. A second task waiting for that monitor could not
  observe cancellation, so the coordinator timeout could finish while the queued engine call remained alive. The lazy
  wrapper also held its monitor throughout delegate inference. Configuration allowed multiple CPU inference threads
  despite the engine being serialized.
- Actual risk: post-timeout work, slow shutdown, queued thread retention, and misleading executor concurrency.
- Reproduction: hold one fake child request open, start a second inference call, then interrupt the second caller. A
  Java monitor wait did not provide the required interruptible acquisition semantics.
- Proposed fix: use `ReentrantLock.lockInterruptibly`, preserve interrupted status, release the lazy initialization
  monitor before calling the delegate, and require exactly one CPU inference thread for this adapter.
- Fix status: **fixed**.
- Test added: `waitingCallerCanBeInterruptedWithoutWaitingForActiveInference` and
  `doesNotHoldInitializationMonitorWhileDelegateInferenceRuns`.

### P2-AUD-003 — model metadata could reach Jlama before structural resource checks

- Severity: **HIGH**
- Location: `ModelDirectory.java`
- Evidence: Phase 1 checked the model type, file names, file sizes, and whether a SafeTensors header was a JSON object,
  but did not validate Qwen dimensions, tensor rank, tensor count, element count, or data offsets.
- Actual risk: a malicious or corrupt local model could request unreasonable allocation, use inconsistent dimensions,
  or hand malformed offsets to the inference runtime. Child-JVM isolation limits application corruption but does not
  prevent local resource exhaustion.
- Reproduction: a test model with a tensor end offset beyond its data region passed the former pre-validation.
- Proposed fix: validate bounded required Qwen dimensions, attention consistency, tensor count/rank/elements, and
  SafeTensors offsets before child start; cap all weights at 1.5 GB total and the header at 16 MB.
- Fix status: **fixed for detectable metadata hazards**. Model files remain executable-like untrusted data and must be
  sourced and checksummed by the user.
- Test added: unsafe dimensions, empty tensor metadata, escaping offsets, and excessive rank.

### P2-AUD-004 — protocol JSON accepted ambiguous or excessively nested input

- Severity: **MEDIUM**
- Location: `Protocol.java`, `JlamaChildMain.java`, `JlamaLocalInferenceEngine.java`
- Evidence: framing bounded bytes, but the mapper used default duplicate-key and nesting behavior.
- Actual risk: duplicate keys could be interpreted differently at validation stages; deep JSON could consume parser
  resources; trailing or unknown fields could weaken versioned-protocol reasoning.
- Reproduction: duplicate `version` fields and a 65-level nested value were accepted by the former mapper.
- Proposed fix: one strict mapper with duplicate detection, depth/name/number/document bounds, unknown-property
  rejection, null-for-primitive rejection, and trailing-token rejection in both parent and child.
- Fix status: **fixed**.
- Test added: duplicate, unknown, deep, malformed UTF-8, truncated, malformed, and oversized frames.

### P2-AUD-005 — exported structured files could be silently truncated

- Severity: **MEDIUM**
- Location: `FileSystemIncidentExporter.java`
- Evidence: each complete JSON/Markdown/text document was passed through the value sanitizer. The sanitizer's maximum
  message length therefore truncated whole documents, potentially producing invalid JSON and removing later evidence.
- Actual risk: incomplete incident bundles and a false expectation that all evidence was exported.
- Reproduction: use a 64-character sanitizer bound; `incident.json` was cut before the evidence array.
- Proposed fix: sanitize every dynamic value before encoding and do not run a per-value truncator over the complete
  document.
- Fix status: **fixed**.
- Test added: the exported JSON remains complete and longer than a deliberately small per-value limit.

### P2-AUD-006 — sanitizer failure could restore the raw throwable type

- Severity: **MEDIUM**
- Location: `ThrowableSnapshotter.java`
- Evidence: when class-name sanitization threw or returned null, the fallback used the original class name.
- Actual risk: this contradicted the fail-closed rule and established a pattern where sanitizer failure can restore
  untrusted data. A Java class name is normally low sensitivity, but hostile throwable implementations are in scope.
- Reproduction: the existing hostile-sanitizer test returned `java.lang.RuntimeException` after sanitizer failure.
- Proposed fix: use a fixed `unknown.throwable` marker and record a bounded capture failure.
- Fix status: **fixed**.
- Test added: throwing and null-returning sanitizer cases.

### P2-AUD-007 — configuration accepted values the implementation cannot safely honor

- Severity: **MEDIUM**
- Location: `DiagnosticsProperties.java`, `InferenceProperties.java`
- Evidence: a zero message limit failed only later while building the sanitizer; inference timeout had no upper bound;
  executor thread count suggested parallel generation despite a serialized child protocol.
- Actual risk: late, less useful startup errors; very long shutdown/cancellation bounds; excess queued threads.
- Proposed fix: validate a positive message limit, cap timeout at one hour, and require one inference thread.
- Fix status: **fixed**.
- Test added: property-binding rejection assertions with useful root-cause messages.

### P2-AUD-008 — host application logging may still expose original exception text

- Severity: **INFORMATIONAL / residual**
- Location: outside StackMate's logging ownership; demonstrated by the sample redaction scenario.
- Evidence: StackMate output is redacted, while Spring Boot's normal exception logger receives the original throwable.
- Actual risk: an application that embeds secrets in exception messages can leak them through its own configured logs.
- Fix status: **not changed by design**. StackMate must preserve normal application logging and must not install an
  appender in this phase. Documentation must state this boundary clearly.

### P2-AUD-009 — Phase 1 model contract gives the model diagnostic authorship

- Severity: **MEDIUM**
- Location: inference API structured output, request factory, prompt resources, renderer.
- Evidence: the model can generate cause/check prose and a language field under one English system instruction. Model
  evaluation also scores category/root-type behavior that deterministic analysis already owns.
- Actual risk: duplicated authority, invented identifiers, unreliable Turkish behavior, and wasted inference on exact
  diagnoses.
- Proposed fix: schema version 2 must permit explanation and selection of supplied IDs only; trusted category, root,
  and locations must always come from the deterministic result; use separate English and Turkish templates and an
  evidence-aware trigger policy.
- Fix status: **scheduled for the mandatory contract-redesign phase**. Corpus V2 must be frozen before analyzer behavior
  changes.

## Public API review

- `stackmate-core` exposes immutable records and the intended analyzer, sanitizer, deduplication, export, renderer, and
  incident contracts. No Spring or inference implementation type crosses its boundary.
- `stackmate-inference-api` exposes engine-neutral immutable contracts. No Jlama, Jackson, process, or model-file type
  occurs in a public signature.
- `stackmate-inference-jvm` has only the concrete engine as its consumer-facing implementation; process/protocol/model
  validation types remain package-private.
- Spring configuration types expose user configuration, not Jlama classes. Bean extension points correspond to actual
  custom-analyzer, sanitizer, renderer, exporter, deduplication, and inference consumers.
- Some core domain records necessarily have public constructors because custom analyzers/exporters consume them. The
  audit found no safe, high-value package-private reduction that would justify compatibility risk in `0.1.0-SNAPSHOT`.
- No breaking public API change was made in this milestone. The Phase 2 inference schema change will be documented as
  an explicit pre-release migration when implemented.

## Dependency and module boundary evidence

`jdeps` on the built artifacts reports:

```text
stackmate-core-0.1.0-SNAPSHOT.jar -> java.base
stackmate-core-0.1.0-SNAPSHOT.jar -> java.sql
stackmate-inference-api-0.1.0-SNAPSHOT.jar -> java.base
stackmate-inference-api-0.1.0-SNAPSHOT.jar -> stackmate-core
```

No core/API source imports Spring, Jackson, SLF4J, Servlet, or Jlama. Relevant runtime dependency-tree excerpts:

```text
stackmate-core
  (no runtime dependencies)

stackmate-inference-api
  \- stackmate-core

stackmate-spring-boot-starter
  \- stackmate-spring-boot-autoconfigure

stackmate-spring-boot-starter-local
  +- stackmate-spring-boot-starter
  +- stackmate-inference-api
  \- stackmate-inference-jvm
       +- jackson-databind 2.21.4
       \- jlama-core 0.8.4
```

The deterministic starter does not pull Jlama. The local starter's Jlama transitive tree is comparatively large and is
an explicit opt-in cost, not a hidden deterministic dependency. Maven Enforcer dependency convergence and banned
dependency rules pass.

## Spring auto-configuration review

- `stackmate.enabled=false` backs off the runtime pipeline.
- servlet/MVC conditions prevent activation for non-web applications and when MVC is absent.
- `@ConditionalOnMissingBean` preserves custom analyzers, sanitizer, renderer, exporter, store, and inference engine.
- missing or invalid model setup fails open through the lazy engine; deterministic diagnostics remain available.
- production-profile guard disables analysis as documented and warns once; it is a convenience guard, not a security
  control.
- generated `spring-configuration-metadata.json` is packaged in autoconfigure.
- lazy model configuration does not validate or start Jlama on the application startup path. Eager initialization is
  explicitly configured and still runs in process setup, not a servlet request.
- the deterministic starter carries autoconfiguration but no MVC starter, so it does not turn a non-web application into
  an MVC application.

## Concurrency and lifecycle review

- Request capture snapshots and renders deterministically before submitting inference.
- The coordinator uses a private bounded daemon executor, a separate private timeout scheduler, abort policy, a circuit
  breaker, and a deduplication reservation.
- Jlama generation is serialized by one interruptible lock; the matching configuration now requires one worker thread.
- the child protocol allows one in-flight exchange and enforces request/response IDs.
- duplicate inference is reserved before submission and released on all observed failure paths.
- coordinator shutdown waits two seconds, then interrupts outstanding work. Engine interruption destroys the child and
  preserves the status. Packaged shutdown/orphan behavior remains a mandatory E2E assertion rather than a source-only
  claim.
- console writes are whole SLF4J events; separate quick/local blocks may interleave with application logs, but their own
  block strings are not written line-by-line.
- export uses per-fingerprint atomic-directory publication and rejects symlinked roots. Concurrent export is bounded by
  unique temporary directories and atomic move semantics.

## Verification performed for this milestone

```text
./mvnw.cmd -pl stackmate-spring-boot-autoconfigure,stackmate-inference-jvm -am test
```

Result after fixes: build success; core 91 tests, inference API 26, inference JVM 25, Spring autoconfigure 69 (211
total after the lazy-wrapper regression test is included). Full clean verification remains required at the packaged E2E
milestone.

## Remaining audit risks

1. Packaged fake-child protocol and orphan cleanup must be proven in a Failsafe lifecycle.
2. Native/Jlama code and malicious model files cannot be made fully safe inside the worker; process isolation, checksums,
   strict size bounds, source trust, and OS controls remain important.
3. Windows is the only locally executed real-model platform at this point.
4. Resolved `@ExceptionHandler`, startup, log-only, and non-servlet exceptions remain outside capture scope.
5. The Phase 1 inference schema remains authoritative until the version-2 contract milestone lands.
