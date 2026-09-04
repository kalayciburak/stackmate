# v0.1.0 public-alpha product scope

StackMate v0.1.0 is a local, deterministic, evidence-backed Spring Boot starter
for concise English console diagnoses of unhandled Servlet/MVC exceptions. It
does not own the application's HTTP response or replace the original exception.

## Supported

- Java 21 and the Spring Boot 4.1 line.
- Servlet applications using Spring MVC.
- Exceptions escaping the documented servlet-filter boundary.
- Bounded throwable-chain and suppressed-exception traversal.
- Root-cause extraction, application-frame ranking and framework-noise
  reduction.
- Structured evidence extraction and deterministic categorization.
- Evidence-strength classification and evidence-linked cause/check candidates.
- Mandatory sensitive-data sanitization and terminal-safe English rendering.
- Stable fingerprints and bounded, thread-safe duplicate suppression.
- Typed configuration metadata, explicit disabled mode and a production-profile
  convenience guard.
- No application-response mutation, cloud service, API key, telemetry, model or
  runtime network request.

## Unsupported

- WebFlux, Spring Boot 3, Java below 21, native image and Kotlin-specific APIs.
- Exceptions fully resolved by `@ExceptionHandler`/`@ControllerAdvice`,
  log-only exceptions, startup failures, scheduled-task failures and async-task
  failures outside the documented capture boundary.
- Logback/Log4j2 capture, service AOP, dashboard, IDE/MCP/agent integration and
  automatic fixes.
- Local or remote LLMs, model downloads, adapters, inference workers and cloud
  providers as product features.
- Replacing an observability platform or guaranteeing a diagnosis for every
  exception family.

## Behavioral invariants

StackMate must remain removable without changing application semantics. It must
rethrow the original failure path, preserve handler precedence, status and body,
remain inert when disabled, fail open on internal errors, avoid unmanaged
threads/processes/sockets, and never emit raw incident data after sanitizer
failure.

Historical inference modules and reports remain reproducibility material only.
They are not on the release allowlist and do not define public-alpha behavior.
