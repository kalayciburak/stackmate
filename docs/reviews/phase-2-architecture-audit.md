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
capture preserves the servlet response, and model output passes a separate validation boundary. The audit found no
critical issue. Across the completed phase it confirmed five HIGH, ten MEDIUM, four LOW, and two informational/residual
findings. Confirmed HIGH and practical MEDIUM findings were fixed. The schema-v2 pre-release inference API break is
intentional and has an ADR/migration note; no unrelated API was changed for aesthetics.

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
- Fix status: **fixed after the corpus V2 freeze**. Schema version 2 accepts only bounded explanation text and known
  cause/check/evidence IDs; category, strength, root type, and application locations remain deterministic. English and
  Turkish now use separate prompt resources, and the default trigger skips exact high-evidence diagnoses.

### P2-AUD-010 — generic root causes outranked framework context

- Severity: **MEDIUM**
- Location: built-in analyzer ordering and database cause traversal.
- Evidence: corpus V2 classified `BeanCreationException -> IllegalStateException`, MVC conversion wrappers, JDBC data
  conversion, and JDBC connection wrappers by their generic deepest Java cause because `GeneralJavaAnalyzer` ran first.
- Actual risk: technically true but less actionable diagnoses hid the framework boundary that supplied the failure
  context.
- Reproduction: the unchanged frozen-corpus baseline categorized only 59/80 cases; Spring creation, MVC conversion, H2
  conversion, and JDBC connection examples selected general Java or generic database causes.
- Proposed fix: order request, Spring, database, security, and infrastructure context before general Java; traverse
  database chains root-first within a verified database wrapper; add only portable class/message rules.
- Fix status: **fixed**. Analyzer versions were incremented so fingerprints reflect the rule-set change.
- Test added: framework precedence, database-wrapped network cause, concrete index subclasses, both Jackson package
  generations, optimistic locking, DNS, TLS-message, and structured property/request evidence.

### P2-AUD-011 — truncation and zero-width characters could weaken secret matching

- Severity: **HIGH**
- Location: `DefaultSanitizer`.
- Evidence: sanitization truncated input before matching. A JWT prefix ending exactly at the configured maximum no
  longer satisfied the complete JWT pattern. Zero-width characters could also split a sensitive key such as
  `pass\u200Bword` before assignment matching.
- Actual risk: a bounded JWT fragment or obfuscated credential could reach console, prompt, or export.
- Reproduction: sanitize a JWT with a 16-character limit, or `pass\u200Bword=hunter2` with defaults.
- Proposed fix: fail closed on a JWT prefix at the truncation boundary and remove zero-width join/space/BOM controls
  before credential matching.
- Fix status: **fixed**.
- Test added: truncated JWT, zero-width credential key, repeated Unicode/control handling.

### P2-AUD-012 — SafeTensors entries were individually bounded but not mutually consistent

- Severity: **HIGH**
- Location: `ModelDirectory` SafeTensors pre-validation.
- Evidence: Phase 1 checked each tensor range against the file but did not prove that ranges were non-overlapping,
  gap-free, fully indexed, or equal to `shape element count * dtype width`.
- Actual risk: corrupt or malicious metadata could reach Jlama with ambiguous tensor buffers or mismatched allocation
  assumptions.
- Reproduction: two U8 tensors both reference `[0,1]`, or an F32 scalar references one byte.
- Proposed fix: accept only known SafeTensors dtypes, verify exact tensor byte size, sort ranges, reject overlap/gaps,
  and require the complete data region to be indexed.
- Fix status: **fixed before child launch**.
- Test added: overlap, gap, unsupported dtype, byte-size mismatch, relative/Unicode path, and path symlink coverage.

### P2-AUD-013 — token and character limits encoded an unsafe fixed-width assumption

- Severity: **MEDIUM**
- Location: `JlamaChildMain.java`, `DefaultAsyncLocalInferenceCoordinator.java`.
- Evidence: the configured output-token limit was converted to a validation envelope using four characters per token,
  and the child converted characters back to tokens with the same fixed ratio. Tokenizers do not provide that invariant.
- Actual risk: otherwise valid JSON could be truncated or rejected, while changing one limit could unexpectedly change
  the other safety boundary.
- Proposed fix: retain independent hard caps, use a conservative eight-character validation envelope and six-character
  generation estimate, and never exceed the configured token or absolute character limits.
- Fix status: **fixed**. The mapping remains an estimate and is documented rather than called exact token accounting.
- Test added: coordinator bound assertions and model benchmark regression coverage.

### P2-AUD-014 — debug fallback hid the sanitized rejection reason

- Severity: **MEDIUM**
- Location: `DefaultAsyncLocalInferenceCoordinator.java`.
- Evidence: debug mode logged only `ENGINE_ERROR` or `INVALID_OUTPUT`, even though the engine and validator held bounded
  reasons. This prevented operators from distinguishing an unknown ID from malformed framing without exposing raw data.
- Actual risk: poor diagnosis of local failures encouraged unsafe prompt/output logging during troubleshooting.
- Proposed fix: log only sanitized, bounded engine failure messages and sorted validator issue codes when debug is
  explicitly enabled; never log prompts or invalid raw output.
- Fix status: **fixed**.
- Test/evidence: packaged malformed-frame and timeout runs show bounded reasons; sanitizer is injected into the
  coordinator boundary.

### P2-AUD-015 — `lazy-load=false` did not eagerly load model weights

- Severity: **LOW / documentation**
- Location: local adapter initialization and configuration documentation.
- Evidence: process startup measurement shows eager adapter validation, but Jlama weights are still demand-loaded on
  the first inference.
- Actual risk: users could expect the first-request cold cost to move entirely to startup.
- Fix status: **documented**. The setting is described as adapter/metadata eager initialization, not eager weight load.

### P2-AUD-016 — schema-v2 made the packaged valid fixture select unsupported evidence

- Severity: **MEDIUM for the validation system; sample fixture only**
- Location: `SampleFakeInferenceChildMain.java`.
- Evidence: the fake worker selected the first fact while also selecting cause/check candidates backed by different
  facts. The stricter validator correctly rejected the nominally valid E2E scenario.
- Actual risk: a false-negative integration fixture could hide lifecycle regressions and make the console gallery lie.
- Proposed fix: select the union of evidence backing each selected deterministic candidate.
- Fix status: **fixed**; packaged E2E is rerun in final verification.

### P2-AUD-017 — evaluated real models do not meet the reduced contract

- Severity: **INFORMATIONAL / product decision**
- Location: local Model Lab V2.
- Evidence: both screened models produced 0/10 valid English and 0/10 valid Turkish structured outputs, with warm
  medians above 17 seconds; sampled worker memory also exceeded target.
- Actual risk: continuing to label the integration merely experimental could be read as an imminent product feature.
- Fix status: **classified `NOT_VIABLE`** for evaluated configurations; deterministic mode remains the product path.

### P2-AUD-018 — unused legacy prompt resources still described model-authored sources

- Severity: **LOW / contract clarity**
- Location: packaged `prompts/v1` and `prompts/v2` resources in `stackmate-inference-jvm`.
- Evidence: runtime `PromptRenderer` loaded only `prompts/schema-v2`, while the unused resources still requested
  `sourceLocationIds` and were packaged in the inference JAR.
- Actual risk: a reviewer or future refactor could mistakenly treat the obsolete source-authoring prompt as supported.
- Fix status: **fixed**. Only the separate English/Turkish schema-v2 prompt resources remain packaged.

### P2-AUD-019 — real-model E2E required an explanation within an incompatible timeout

- Severity: **MEDIUM / validation integrity**
- Location: `PackagedRealModelIT`, `PackagedSample`.
- Evidence: the profile allowed 30 seconds for inference but used the common eight-second console wait and required a
  local-explanation block. Both screened models take more than eight seconds and produce rejected schema-v2 output.
- Actual risk: the explicit profile could not validate the actual `NOT_VIABLE` configuration or safe fallback and
  would fail for a reason unrelated to process safety.
- Fix status: **fixed**. The profile now bounds the wait at 45 seconds, accepts either validator-approved explanation
  or sanitized fallback, still requires HTTP completion within two seconds, and verifies child cleanup.
- Verification: Qwen2.5-0.5B-Instruct packaged lifecycle passed in 30.385 seconds on Windows.

### P2-AUD-020 — several named adversarial cases had only indirect coverage

- Severity: **LOW / test evidence**
- Location: sanitizer, model-directory, protocol, and engine tests.
- Evidence: repeated/already-redacted text, missing/oversized/unexpected model artifacts, read-only long Unicode paths,
  Unicode frames, stdout logs around responses, and exit during a partial response lacked direct assertions.
- Actual risk: broad tests could pass while a named Phase 2 acceptance case regressed.
- Fix status: **fixed** with focused tests; no production behavior change was required.

### P2-AUD-021 — the detailed threat-model review retained Phase 1 pending language

- Severity: **LOW / documentation integrity**
- Location: `docs/reviews/stackmate-threat-model.md`.
- Evidence: risk text still called schema v2 pending after the schema had shipped and did not fully separate runtime,
  local model, export, and build trust boundaries.
- Fix status: **fixed** using the repository-grounded threat-model contract with current evidence anchors, explicit
  attacker capabilities/non-capabilities, prioritized abuse paths, and remaining OS-sandbox risks.

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
- The inference schema-v2 change intentionally removed model-authored diagnostic/source types from the pre-release API.
  ADR 0012 and the migration section in the inference API README record the break. No Jlama or Spring implementation
  type was introduced into the public contract.

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

1. Native/Jlama code and malicious model files cannot be made fully safe inside the worker; process isolation, checksums,
   strict size bounds, source trust, and OS controls remain important.
2. Windows is the only locally executed real-model platform; remote CI configuration is not runner evidence.
3. Resolved `@ExceptionHandler`, Spring MVC resolver, startup, log-only, and non-servlet exceptions remain outside
   capture scope.
4. Packaged orphan cleanup is covered by Failsafe on Windows locally, but Linux/macOS runner execution is unverified.
5. Targeted adversarial tests do not replace sustained fuzzing or an OS process/network sandbox.

## Final four-way review

### Architecture

The final reactor contains ten child modules plus the parent. Dependency direction is acyclic, the deterministic
starter remains inference-free, engine types do not cross the inference API, and the E2E module is intentionally a
Failsafe test module rather than a published product JAR. No breaking public API change was introduced merely to
reduce visibility. The reduced schema-v2 contract is an intentional pre-release contract correction documented by
ADR 0012.

### Security and privacy

The review traced all untrusted model/process strings through bounded sanitization, strict response validation, and
debug-only sanitized failure reasons. Exact SafeTensors metadata validation, symlink rejection, terminal-control
stripping, zero-width key normalization, repeated redaction, unknown-ID rejection, and invalid-output fallback have
focused regression coverage. The remaining native/model-file and OS-sandbox limitations are documented rather than
hidden.

### Performance and lifecycle

Inference is submitted to a bounded dedicated executor and never runs on the servlet thread. Default policy skips
high-confidence exact diagnoses and low/insufficient incidents. Packaged tests cover queue saturation, timeout,
unexpected exit, shutdown during inference, duplicate suppression, and process-tree cleanup. JMH and fresh-JVM
startup measurements are environment evidence, not portable build gates.

### Developer experience

The README quickstart, packaged E2E suite, and console gallery distinguish trusted deterministic facts from optional
model prose. English and Turkish renderers remain independently usable. The gallery explicitly shows that validation
and malformed-body errors resolved inside Spring MVC are not captured at the current boundary; the product does not
claim otherwise. The evaluated real-model path is labeled `NOT_VIABLE`, while deterministic diagnostics are the
recommended product path.

The completion-audit clean verification passed 314 Surefire tests and 12 normal-CI Failsafe tests with no failures,
errors, or skips. The separately enabled real-model Failsafe test also passed. Exact module counts and coverage are
recorded in `docs/phase-2-progress.md`.
