# StackMate implementation progress

Last updated: 2026-08-01 (Europe/Istanbul)

This is the live, evidence-oriented implementation log. Target values are not reported as measurements. Phase
entries are append-only snapshots: later entries supersede earlier `not implemented` or `not measured` statements.

## Phase 0 - Discovery and evidence

### Completed

- Inspected the workspace: it was empty and was not yet a Git repository.
- Verified the host toolchain:
  - Git 2.55.0.windows.3 is available.
  - The default `java` command resolves to Amazon Corretto 17.0.20.
  - Amazon Corretto 21.0.12 is installed at `<jdk-21-home>`.
  - No system `mvn` command is installed, so the Maven Wrapper is required for all builds.
- Verified from the current official Spring Boot documentation that:
  - Spring Boot 4.1.0 is the latest stable 4.1.x release visible on 2026-08-01.
  - Spring Boot 4.1.1 is still listed as a snapshot.
  - Spring Boot 4.1.0 requires Java 17 or later and Spring Framework 7.0.8 or later.
  - StackMate will nevertheless compile and run on the explicitly required Java 21 baseline.
- Confirmed the Boot 4.1 auto-configuration registration contract:
  `@AutoConfiguration` plus
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Started primary-source research for the reference repository, JVM inference runtimes,
  small model candidates, Maven Wrapper version, and basic name/artifact collisions.
- Inspected `kalayciburak/auth-service` at commit
  `6e03087dfae923ae1940361486fea88ae509fc09` and recorded adopted/rejected style decisions in ADR 0002.
- Verified Apache Maven 3.9.16 as the current stable Maven 3.9.x release and Maven Wrapper Plugin 3.3.4
  as the current official wrapper plugin.
- Found an existing unrelated `stackmate-io/stackmate` infrastructure product and `docs.stackmate.io`.
  The required project name is retained, but it is not treated as a collision-free global brand.
- Found no public Maven Central artifact at `io.github.kalayciburak:stackmate` and no positively visible public
  repository at `github.com/kalayciburak/stackmate`; these negative checks do not reserve either name.
- Compared Jlama 0.8.4, llama3.java, and ONNX Runtime GenAI 0.15.0 from primary sources.
- Selected Jlama 0.8.4 as the single experimental adapter and a managed Java 21 child JVM using stdin/stdout IPC.
- Compared current small model candidates and selected Qwen2.5-0.5B-Instruct as the first Jlama-compatible
  benchmark candidate. No model was downloaded or run in Phase 0.
- Verified current stable build-plugin versions from official Maven metadata and recorded the Boot-managed
  JUnit Jupiter 6.0.3, AssertJ 3.27.7, and Testcontainers 2.0.5 versions.

### Commands and results

```text
Get-ChildItem -Force
Result: WORKSPACE_EMPTY

git status --short --branch
Result: fatal: not a git repository

git --version
Result: git version 2.55.0.windows.3

java -version
Result: openjdk version "17.0.20" (default PATH; not suitable for this build)

<jdk-21-home>\bin\java.exe -version
Result: openjdk version "21.0.12" 2026-07-21 LTS

mvn -version
Result: command not found
```

### Initial decisions

- Build against Spring Boot 4.1.0 and Java 21.
- Keep `stackmate-core` independent of Spring, logging, JSON, servlet, and inference runtimes.
- Make deterministic evidence the only source of diagnostic strength.
- Prohibit runtime network access and automatic model downloads.
- Keep model weights user-supplied and outside all artifacts.
- Make the real inference adapter optional; all normal verification must work with a deterministic fake.
- Keep Jlama and all of its transitive dependencies outside the deterministic starter.
- Run inference in a managed child JVM so preview/vector flags and model memory do not leak into the application JVM.
- Keep inference status `EXPERIMENTAL` until an actual model passes the mandatory A/B/C benchmark gates.
- Pin the wrapper distribution to Apache Maven 3.9.16.
- Retain the mandated StackMate name while documenting the existing unrelated brand collision and avoiding
  claims of global uniqueness.

### Tests executed

None yet. Production implementation and Maven project bootstrap have not started.

### Remaining risks

- Jlama requires preview/vector runtime flags, and packaged executable-JAR worker discovery is not yet verified.
- A useful permissively licensed sub-1.5B model may still fail the quality gates.
- The currently selected shell Java is 17; every verification command must explicitly select Java 21.
- Real local-model benchmarks require an explicit user-supplied model file and may remain unexecuted if none is available.
- Runtime no-network behavior needs both architectural enforcement and verification.

### Next phase

Complete Phase 0 research and ADRs, then bootstrap the Maven reactor and run the first Java 21 wrapper build.

## Phase 1 - Repository bootstrap

### Completed

- Initialized a new Git repository on branch `main`; there was no existing user history to rewrite.
- Created the requested eight-module Maven reactor with one-way internal dependency management.
- Generated Maven Wrapper 3.3.4 scripts pinned to Apache Maven 3.9.16.
- Added Java 21 compiler release, compiler warnings, Maven/Java enforcer gates, dependency convergence,
  banned Lombok/Spring AI/LangChain4j dependencies, Surefire/Failsafe, JaCoCo, Spotless, and reproducible timestamp metadata.
- Added Apache License 2.0, `NOTICE`, initial third-party notices, model-weight ignores, and English/Turkish README entry points.
- Kept Jlama as a runtime dependency of `stackmate-inference-jvm`; it is optional from auto-configuration and is
  not a declared dependency of the deterministic starter.

### Commands and results

```text
Maven Wrapper Plugin 3.3.4 with -Dmaven=3.9.16
Result: BUILD SUCCESS; wrapper URL pinned to apache-maven-3.9.16-bin.zip

.\mvnw.cmd clean verify  (Java 21)
First result: BUILD FAILURE at Spotless because Markdown files contained an extra final blank line.

.\mvnw.cmd spotless:apply  (Java 21)
Result: BUILD SUCCESS; 13 Markdown files and reactor POM formatting normalized.

.\mvnw.cmd clean verify  (Java 21)
Result: BUILD SUCCESS; 9/9 reactor projects succeeded in 4.304 s.
```

The last duration is a build observation on this machine, not a product performance benchmark.

### Tests executed

No Java tests existed at the bootstrap milestone. Maven reported `No tests to run`; the successful build proves
the reactor/tooling baseline only. Empty-JAR warnings are expected temporarily and must disappear as implementation
lands in the next phases.

### Remaining risks

- Module dependency trees must be re-inspected after production classes and optional auto-configuration land.
- Coverage gates are configured for core but have not exercised any code yet.
- Starter artifacts are temporarily empty aggregators; their meaningful dependency boundaries are implemented next.

### Next phase

Implement the tested Spring-independent incident pipeline, then integrate it through a non-resolving servlet MVC adapter.

## Phase 2 - Core incident pipeline

### Completed

- Added immutable incident, request, Spring runtime, JVM runtime, throwable, suppressed-exception, stack-frame,
  SQL exception detail, evidence, diagnosis, fingerprint, and duplicate occurrence contracts.
- Implemented defensive throwable capture with cause-cycle detection, cause/suppressed/node/frame limits,
  wrapper preservation, interrupted-status preservation, severe-JVM-failure identification, and hostile throwable guards.
- Implemented application-frame ranking by deepest causal location with explicit package ownership,
  generated/proxy noise filtering, stable deduplication, and bounded results.
- Implemented bounded sanitization for control/terminal sequences, named credentials, Bearer/JWT values,
  JDBC credentials, long secret-like values, configurable patterns, email, Turkish identity-number-like values,
  and payment-card-like values. Definite secret and custom-pattern redaction also applies to hostile stack-frame identities.
- Added ordered, versioned, stateless analyzer SPI and fail-open contribution aggregation.
- Added deterministic analyzers for required Java, Spring DI/configuration, MVC/validation/serialization,
  JDBC/persistence, security, connection, TLS, DNS, timeout, locking, concurrency, and resource failure families.
- Added evidence-backed unknown fallback containing only the captured root type, sanitized root message,
  application location, cause relationships, and an explicit insufficient-context statement.
- Added SHA-256 fingerprinting with volatile-value normalization and a bounded, concurrent, TTL-based
  in-memory duplicate store with atomic inference reservation.
- Added the renderer-neutral deterministic engine that composes diagnosis and fingerprint output.
- Confirmed by source scan that core production code imports no Spring, Servlet, Jackson, SLF4J, Jlama,
  database-driver, or security-framework APIs.

### Commands and results

```text
.\mvnw.cmd -pl stackmate-core test  (Java 21)
Result: BUILD SUCCESS; 89 tests, 0 failures, 0 errors, 0 skipped.

.\mvnw.cmd -pl stackmate-core spotless:apply  (Java 21)
Result: BUILD SUCCESS; the module's Java sources and tests were normalized by the pinned formatter.

.\mvnw.cmd -pl stackmate-core verify  (Java 21)
Result: BUILD SUCCESS; tests, formatting, JAR creation, and JaCoCo gates passed.

git diff --check
Result: clean.
```

### Coverage

- Line coverage: 1,634 / 1,691 = 96.63%.
- Branch coverage: 646 / 737 = 87.65%.

These values come from `stackmate-core/target/site/jacoco/jacoco.csv` generated by the verified module run.

### Decisions made

- Keep optional framework recognition based on exact exception class names, bounded message patterns,
  and JDK `SQLException` metadata so core remains JDK-only.
- Treat exact structured evidence as stronger than message-only patterns; the latter cannot produce HIGH strength alone.
- Keep duplicate state instance-local with no static mutable state or background cleanup thread.
- Keep incident IDs adapter-owned and fingerprints content-derived; an incident ID is not a fingerprint input.
- Capture servlet exceptions with a non-mutating filter rather than claiming visibility into all MVC-resolved errors;
  the exact visibility contract is recorded in ADR 0009.

### Remaining risks

- Vendor messages outside the conservative bounded patterns fall back to a general or unknown diagnosis.
- The bounded deduplication store intentionally forgets the oldest fingerprint when capacity is exceeded, which can
  permit re-inference under extreme fingerprint cardinality.
- The Spring adapter must always provide at least one safely detected application package to the frame selector.
- Severe JVM failures still need the adapter's minimal low-allocation rendering path; they must not enter inference.

### Next phase

Implement and integration-test the Spring Boot 4.1 servlet auto-configuration, non-mutating capture filter,
typed properties, production guard, runtime context extraction, and deterministic English/Turkish console output.

## Phase 3 - Spring Boot runtime integration

### Completed

- Added Boot 4.1 `@AutoConfiguration` registered through the official `AutoConfiguration.imports` resource.
- Added servlet/MVC/class/property conditions and `@ConditionalOnMissingBean` extension points.
- Added bounded typed `stackmate.*` properties and generated configuration metadata.
- Added a `OncePerRequestFilter` that observes exceptions escaping the servlet chain, rethrows the exact same
  object, skips error dispatch, uses an instance-local recursion guard, and never writes to the response.
- Added minimal request, handler, selected MDC, active profile, application package, Boot/Spring version,
  and optional transaction-state extraction. No body, query values, headers, cookies, session, environment values,
  or bean inventory are collected.
- Added a production-profile guard that prevents analysis by default and emits at most one warning per handler bean.
- Added developer extension beans for custom analyzers, sanitizers, renderers, capture handlers, stores, and pipeline parts.
- Added real MVC integration tests proving normal requests and application `@ExceptionHandler` status/body behavior
  remain unchanged, while an escaping exception is observed and rethrown with the same identity.

### Verification

```text
.\mvnw.cmd -pl stackmate-spring-boot-autoconfigure -am test  (Java 21)
Result: BUILD SUCCESS; 89 core tests and 42 Spring adapter tests passed.
```

The Spring tests cover enabled/disabled conditions, missing MVC classes, user bean override, custom analyzers,
missing inference engine, invalid property binding, production guard, configuration metadata, normal requests,
handled exceptions, escaping exceptions, fail-open capture, error dispatch, context bounds, and response preservation.

### Startup failure decision

Spring Boot's `FailureAnalyzer` mechanism was confirmed, but a catch-all StackMate analyzer was not registered in
this phase because it could preempt Boot's more specific built-in analyzers before the StackMate application context
and user privacy configuration exist. Runtime MVC completion remains independent. A future startup adapter must use
narrow supported failure types or a non-preempting reporting integration.

## Phase 4 - Deterministic console MVP

### Completed

- Added English and natural Turkish output, BOX and COMPACT styles, bounded width, technical-detail controls,
  application trace, source/runtime labels, and explicit network-disabled status.
- Added stable displayed incident IDs derived from the fingerprint rather than capture-specific IDs.
- Re-sanitize every untrusted value at the renderer boundary and emit no ANSI/control sequences.
- Added concise duplicate output and a minimal severe-JVM-failure path that skips inference.
- Added the executable Spring Boot sample with all 14 required demo routes and six configuration profiles.
- Simplified source locations to `SimpleClass#method(File.java:line)` for console scanning while preserving evidence.

### Commands and results

```text
.\mvnw.cmd -pl stackmate-sample -am package -DskipTests  (Java 21)
Result: BUILD SUCCESS; executable Boot JAR created.

java -jar stackmate-sample/target/stackmate-sample-0.1.0-SNAPSHOT.jar --server.port=18087
Result: application started on Java 21.0.12, Spring Boot 4.1.0, Tomcat 11.0.22.

GET /demo/null-pointer
GET /demo/redaction
GET /demo/repeated  (twice)
Result: HTTP 500, 500, 500, 500. The original Boot error behavior and stack traces remained present.

Observed StackMate behavior:
- Quick diagnosis printed for the first occurrence.
- Controlled password, Bearer/JWT-like value, JDBC credentials, email, and Turkish identity value were redacted
  in StackMate output.
- The repeated incident printed only `StackMate duplicate ... (count 2)` on its second occurrence.
- The sample process was stopped after verification.

.\mvnw.cmd spotless:apply  (Java 21)
Result: BUILD SUCCESS.

.\mvnw.cmd clean verify  (Java 21)
Result: BUILD SUCCESS across all 9 reactor projects in 13.832 s; 89 core, 42 Spring, and 16 sample tests passed.
```

The build duration is an observation on this machine, not a product latency benchmark.

### Remaining risks

- StackMate sanitizes its own output. It deliberately preserves the application's normal logger output, so an
  application exception message containing a secret can still be exposed by the application's existing raw stack trace.
- The current filter cannot observe exceptions fully handled by MVC resolvers or application exception handlers;
  ADR 0009 documents the exact visibility boundary.
- Local inference is not implemented yet; the local starter currently contributes only dependency boundaries.
- Normal tests use Boot's test stack, which emits a JDK warning about Mockito/Byte Buddy dynamic agent attachment.

### Next phase

Create the controlled 50-case corpus, deterministic evaluation harness, machine/human reports, and JMH benchmarks
before adding production local inference.

## Phase 5 - Benchmark corpus

### Completed

- Added the versioned `phase5-50-v1` corpus with exactly 50 controlled cases and the required
  `8/8/9/11/4/5/5` domain distribution.
- Added immutable ground-truth definitions for expected category, root type, evidence facts, cause IDs, check IDs,
  and forbidden claims.
- Added 29 real JDK/SQL/network/concurrency throwable scenarios and 21 optional-framework snapshot scenarios,
  all with sanitized inputs and controlled application locations.
- Added a deterministic Mode C evaluator with exact category/root/fact/cause/check and safety counters.
- Added JSON and Markdown report generation under the ignored module `target` directory plus an English/Turkish
  manual review worksheet.
- Added JMH 1.37 suites for throwable capture, sanitization, frame selection, fingerprinting, deterministic diagnosis,
  and console rendering. Fixture construction is outside measured methods.
- Kept Modes A and B explicitly not executed because no local model is configured.

### Commands and results

```text
.\mvnw.cmd -pl stackmate-benchmark -am verify  (Java 21)
Result: BUILD SUCCESS in 7.354 s; 89 core, 42 Spring adapter, and 4 benchmark tests passed.

git diff --check
Result: clean.
```

### Actual controlled-corpus result

- Cases: 50.
- Category/root/fact-exact/cause/check accuracy: 50/50 (100%).
- Evidence precision and recall: 61/61 (100%).
- Fabricated source references: 0.
- Unknown IDs: 0.
- Forbidden claims: 0.
- Controlled secret leakage: 0.
- Prompt-injection sentinel leakage: 0.
- Mode A raw-model benchmark: not executed; no model.
- Mode B structured-model benchmark: not executed; no model.
- JMH average time on Ryzen 9 7950X / Corretto 21.0.12 / Windows 11: frame selection 1.30 us/op,
  deterministic diagnosis 20.56 us/op, fingerprint 15.69 us/op, sanitizer 50.37 us/op,
  throwable snapshot 114.56 us/op, and detailed rendering 149.60 us/op.
- JMH used two forks with five one-second warmup and five one-second measurement iterations per benchmark.
- Model latency and model memory: not measured.

This is a supported-rule regression baseline over controlled ground truth, not a claim of 100% accuracy on arbitrary
Spring applications.

### Remaining risks

- The framework snapshot cases do not replace real integration scenarios for every optional framework.
- JMH results are isolated algorithm timings and do not include servlet dispatch or logger I/O.
- Model comparison and all local-inference safety gates remain unverified until the inference adapter and a reviewed
  local model are available.

### Next phase

Implement the stable inference contracts, validated structured-output boundary, managed JVM adapter, bounded async
execution, and fake-engine integration tests. Run real model modes only when a licensed local model can be executed.

## Evidence sources

- Spring Boot system requirements: https://docs.spring.io/spring-boot/system-requirements.html
- Spring Boot custom auto-configuration: https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html
- Spring Boot 4.1 release notes: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes
- Apache Maven downloads: https://maven.apache.org/download.cgi
- Maven Wrapper: https://maven.apache.org/tools/wrapper/index.html
- Sonatype namespace registration: https://central.sonatype.org/register/namespace/
- Existing unrelated Stackmate product: https://docs.stackmate.io/

## Phases 6 and 7 - Local inference feasibility and integration

### Completed

- Compared Jlama, llama3.java, and ONNX Runtime GenAI from their primary repositories, published artifacts,
  license files, platform notes, and model-format documentation.
- Selected Jlama 0.8.4 for one experimental adapter and Qwen2.5-0.5B-Instruct for the first measured candidate.
- Added engine-neutral inference records, availability/failure models, output validation, circuit breaking, and a
  versioned prompt that treats all incident content as untrusted data.
- Added a managed Java 21 worker using bounded framed stdin/stdout, lazy model loading, timeout, output limits,
  dedicated single-thread bounded execution, graceful shutdown, and deterministic fallback.
- Kept the deterministic starter free of inference/Jlama/Jackson dependencies and made hybrid mode explicit.
- Executed the official SafeTensors model with no model download in runtime code and no weights in Git.

### Commands and actual results

```text
.\mvnw.cmd --batch-mode --no-transfer-progress -pl stackmate-benchmark -am -Pmodel-benchmark
  -DskipTests -Dstackmate.model.path=<local-model>
  -Dstackmate.benchmark.timeout-seconds=60 -Dstackmate.benchmark.language=en
  -Dstackmate.benchmark.maximum-cases=50 verify
Result: BUILD SUCCESS in 18:52; prompt v2 evaluated 50/50 English cases.

The same command with -Dstackmate.benchmark.language=tr and maximum-cases=5
Result: BUILD SUCCESS in 2:24; prompt v2 evaluated a five-case Turkish sample.
```

English Mode A raw input produced 8/50 valid outputs, 1/50 correct categories, 38 engine failures, 4 invalid
outputs, two generated-candidate leakage detections, 14,498 ms median latency, and 14,858 ms P95 latency. English
Mode B structured input produced 36/50 valid outputs, 36/50 category/cause/check accuracy, 19/50 root-type text
accuracy, five engine failures, nine invalid outputs, zero controlled leakage, 7,213 ms median latency, and
18,933 ms P95 latency. All unknown-ID and fabricated-source outputs were rejected. Mode C remained 50/50 across
category, root, exact facts, cause, and check. Structured input therefore clearly beats raw input but misses the
quality target.

The five-case Turkish sample produced zero valid model outputs in both raw and structured modes. Four structured
outputs were rejected for English prose and one for invalid engine output. The selected descriptor now advertises
English only; deterministic Turkish output remains supported.

Peak sampled working set during the English corpus was 2,790.19 MiB for the worker and 2,972.36 MiB for parent plus
worker. Sampled TCP/UDP endpoints remained zero. The official 988,097,824-byte `model.safetensors` SHA-256 was
`FDF756FA7FCBE7404D5C60E26BFF1A0C8B8AA1F72CED49E7DD0210FE288FB7FE`. The result is `EXPERIMENTAL`.

## Phases 8 and 9 - Hardening and incident export

### Completed

- Added duplicate inference reservation, bounded queue rejection, timeout/circuit-breaker fallback, one-time model
  warnings, recursion protection, interrupt preservation, severe-JVM-error bypass, and worker session cleanup.
- Added output validation for schema, lengths, language, evidence/cause/check/source allow-lists, control text,
  forbidden claims, and secret leakage.
- Added optional four-file sanitized incident bundles using fixed names, `CREATE_NEW`, a temporary sibling directory,
  atomic publication, symlink rejection, safe retention, and no source copy or agent invocation.
- Wrote and reviewed the privacy/threat model for leakage, prompt injection, model files, resource exhaustion,
  recursive logging, filesystem races, process failures, and the zero-runtime-network policy.

### Demonstrations and results

- Deterministic sample: database duplicate, repeat, redaction, and prompt-injection endpoints all preserved Spring
  Boot's HTTP 500 JSON response. StackMate printed one quick diagnosis and one concise duplicate message; controlled
  secrets were absent from StackMate output. The application's unchanged raw log can still contain application
  secrets and is documented as outside StackMate's logging boundary.
- Turkish sample: the database-constraint diagnosis rendered localized headings, summary, details, and checks while
  preserving the captured root message as evidence.
- Disabled sample: HTTP 500 behavior remained and no StackMate output appeared.
- Hybrid real-model sample: the first request returned HTTP 500 in 252 ms, the duplicate in 33 ms, and one validated
  local explanation printed 9,331 ms later on `stackmate-local-inference-1`. Exactly one inference was executed;
  the child had no sampled network endpoint and exited with its parent.

## Phases 10 and 11 - Documentation, CI, and final review

### Completed before final verification

- Added full English and Turkish READMEs, architecture, benchmark, model selection, troubleshooting, privacy,
  community, license/notice, changelog, and ADR documentation.
- Added Linux/Windows verification, macOS smoke, dependency review, and manually triggered model-benchmark workflow
  definitions. These workflows are configured but have not run on remote GitHub infrastructure.
- Added Enforcer, dependency convergence, Java 21 enforcement, Spotless, Surefire/Failsafe, JaCoCo, compiler
  warnings, reproducible-build metadata, and module-specific coverage gates.
- Verified the deterministic starter runtime tree contains no inference, Jlama, or Jackson dependency. The local
  starter contains the optional inference API/Jlama/Jackson stack as intended.
- Verified the inference-JVM module at 75.31% line coverage after excluding only the separate child main from the
  unit-test denominator; that child main is exercised by the explicit real-model managed-process benchmark.

The final clean-reactor command, aggregate test counts, coverage summary, repository status, and explicit review
findings are appended after the last verification pass.

### Final verification and four reviews

```text
$env:JAVA_HOME='<jdk-21-home>'
.\mvnw.cmd --batch-mode --no-transfer-progress spotless:apply
Result: BUILD SUCCESS in 1.361 s.

.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
Result: BUILD SUCCESS in 25.448 s across all nine reactor modules.
```

- Tests: 226 run, 0 failures, 0 errors, 0 skipped. Module totals were core 91, inference API 26,
  inference JVM 21, auto-configuration 67, sample 16, and benchmark 5.
- Coverage: core 96.36% line / 87.58% branch; inference API 91.12% / 75.50%; inference JVM
  75.44% / 61.36%; auto-configuration 82.25% / 60.81%; sample 93.10% line; benchmark tooling
  30.20% / 13.53%. Enforced gates passed for core line/branch and all production API/adapter modules' line coverage.
- Configuration metadata: 46 properties and 11 groups were generated.
- Architecture review: core/API forbidden imports were absent; reactor convergence passed; deterministic starter
  tree contained only core, Boot auto-configuration/Framework, and SLF4J; vendor inference types stayed isolated.
- Security review: no runtime network-client import or tracked model weight was found; `git diff --check` passed;
  custom application redaction patterns were additionally wired into model-output validation and regression tested.
- Performance review: no inference call occurs on the request thread; bounded executor/queue, timeout, worker heap,
  circuit breaker, dedup reservation, and shutdown behavior were inspected against the live hybrid run. Total model
  memory and latency remain failed gates.
- Developer-experience review: deterministic, Turkish, disabled, invalid/missing-model fallback, and packaged hybrid
  modes were exercised. Original HTTP 500 behavior remained unchanged and the validated local result arrived later
  in the same console.
- Cross-platform status: Windows Java 21 was executed locally. Linux/Windows verification and macOS smoke workflows
  are committed but were not run remotely because nothing was pushed.
