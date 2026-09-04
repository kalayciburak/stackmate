# StackMate 0.1.0-rc.1 release notes

Status: local release candidate; not published or signed.

StackMate 0.1.0-rc.1 is a deterministic Spring Boot 4.1 starter for Java 21
Servlet/MVC applications. It captures exceptions that escape the servlet
filter boundary, builds bounded evidence, sanitizes it and emits concise English
console diagnostics while preserving the original application failure and HTTP
behavior.

## Included public artifacts

- `io.github.kalayciburak:stackmate-parent`
- `io.github.kalayciburak:stackmate-core`
- `io.github.kalayciburak:stackmate-spring-boot-autoconfigure`
- `io.github.kalayciburak:stackmate-spring-boot-starter`

Local/remote inference, Jlama, model files, prompts, adapters, samples,
benchmarks, E2E fixtures and research outputs are not public release artifacts.

## Defaults and safety

Adding the starter enables deterministic diagnostics by default. Set
`stackmate.enabled=false` for an inert starter. Active `prod` or `production`
profiles block analysis unless
`stackmate.safety.allow-in-production=true` is explicitly configured.

The mandatory primary sanitizer, bounded parsing/snapshots/deduplication and
collections, strict unknown-property binding, literal-only custom redaction and
fail-open custom analyzers are part of the RC contract. The public starter has
no cloud, remote provider, model, telemetry or runtime network path.

StackMate sanitizes its own diagnostic rendering and local export; it is not a
global log-security boundary. Since the original exception is rethrown to
preserve behavior, Tomcat, other frameworks, application code or observability
agents may independently log the original exception message. Applications must
secure those logging paths separately.

## RC evidence

- Public-release clean install passed for all four public coordinates; RC
  sources, Javadoc and SBOMs were produced where applicable.
- The reviewed API contains 73 public types: 56 stable API, 6 extension SPI and
  11 configuration API types. Signature SHA-256:
  `41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7`.
- The external RC consumer passed 6/6 contract tests and 1/1 packaged-process
  test.
- The final public RC SBOM OSV scan exited 0 with zero known vulnerability
  groups across core (0 packages), auto-configuration (18) and starter (14).
- In the RC `/failure` probe, StackMate's rendered block contained zero raw
  synthetic-secret occurrences and showed `password=[REDACTED]`; Tomcat later
  logged the rethrown original exception with that synthetic secret, confirming
  the documented framework-logging boundary.
- A 7-fork fresh-JVM retained-heap probe passed with the bounded deduplication
  cache full at 10,000 entries. Its approximate median was 510,936 bytes
  (about 0.49 MiB) and p95/max was 1,226,608 bytes (about 1.17 MiB); heap delta
  includes JVM noise and is not exact object layout.

## Release qualification

The full clean reactor passed 543 Surefire plus 5 Failsafe tests with zero
failures, errors, or skips. Two independent clean public-release builds produced
18 byte-identical compared payload files with zero differences. The final local
bundle is assembled with artifact, SBOM, API, allowlist, reproducibility and
SHA-256 evidence.

Remote CI/CodeQL/dependency-review/release-preflight results remain unverified.
No signing was performed, no signing key was requested, and nothing was pushed
or published. Treat this as a locally qualified, Windows-only public-alpha RC;
do not infer cross-platform qualification.
