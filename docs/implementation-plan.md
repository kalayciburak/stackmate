# StackMate implementation plan

The work follows the requested phases and does not treat a generated skeleton as completion.

## Quality gates used throughout

1. Evidence before prose: every diagnosis claim links to captured evidence.
2. Fail open: StackMate failures never replace the application's exception or HTTP behavior.
3. Bounded work: snapshot, sanitization, queues, cache, prompts, model output, and export are all bounded.
4. Privacy before convenience: unsafe request fields are not collected, and every output boundary sanitizes again.
5. Dependency direction: Spring adapter -> optional inference integration -> inference API -> core.
6. Verification before claims: tests, benchmarks, and platform support are reported only when executed or directly evidenced.

## Execution sequence

1. **Discovery and evidence** - verify framework/runtime versions, inspect the reference project,
   compare inference engines and models, and record decisions.
2. **Repository bootstrap** - create the Maven reactor, wrapper, module boundaries, legal files,
   dependency/plugin management, and a buildable baseline.
3. **Deterministic core** - implement immutable snapshots, defensive throwable traversal,
   sanitization, evidence, analyzers, fingerprinting, and bounded deduplication with tests.
4. **Spring MVC adapter and console MVP** - observe unresolved servlet MVC exceptions without
   resolving them, extract bounded Spring context, render English/Turkish output, and prove the sample flow.
5. **Benchmark corpus** - add at least 50 ground-truth cases before production model integration.
6. **Inference feasibility and selection** - spike serious JVM-local candidates and record measured or
   source-backed trade-offs; classify inference as READY, EXPERIMENTAL, or NOT_VIABLE.
7. **Optional hybrid pipeline** - add engine-neutral contracts, one selected adapter, structured prompts,
   output validation, a dedicated bounded executor, timeout, circuit breaker, and duplicate suppression.
8. **Hardening and export** - test storms, failures, injection, leakage, concurrency, shutdown, and paths;
   then add the disabled-by-default sanitized exporter.
9. **Documentation, CI, and final review** - complete English/Turkish documentation, CI, dependency and
   coverage checks, then perform explicit architecture, security, performance, and developer-experience reviews.

## Verification strategy

- Normal build: `./mvnw clean verify` (PowerShell: `.\mvnw.cmd clean verify`) on Java 21.
- Normal CI uses a deterministic fake inference engine and never downloads model weights.
- Model acceptance is a separate explicit profile requiring an absolute user-supplied model path.
- Docker-backed sample/database verification is isolated behind a documented profile unless CI reliability is proven.
- JMH is used for deterministic microbenchmark claims; wall-clock demo timings are labeled as observations only.

## Stop conditions

The implementation pauses only for a destructive operation outside the repository, a credential or gated model,
a restrictive license decision, risk of overwriting user-owned work, or a proven technical impossibility.
