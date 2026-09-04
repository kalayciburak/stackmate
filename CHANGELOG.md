# Changelog

All notable public product changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). The project intends to follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) after the first stable release.

## [Unreleased]

### Changed for 0.1.0-rc.2 qualification

- Kept deterministic runtime behavior and the four-coordinate public allowlist unchanged.
- Added README-based sources and Javadoc placeholder JARs for the dependency-only starter, as required by current
  Maven Central rules for non-POM artifacts.
- Added full hosted Linux, Windows and macOS qualification, external-consumer checks, API/artifact evidence,
  seven-day artifact retention, Ubuntu OSV/performance smoke and same-OS reproducibility.
- Made release preflight manual-only and kept publication/signing absent.
- Migrated CodeQL to immutable official v4.37.9 with read-only workflow metadata access and local SARIF retention.
- Made the packaged performance harness portable across Windows, Linux and macOS PowerShell.
- Corrected historical inference tests for Unix separators, Windows cross-drive temp paths and macOS system path
  aliases without adding inference to the public release.
- Replaced unavailable private-repository dependency-review integration with a PR base/head declaration diff and
  fail-closed public runtime dependency audit.
- Stabilized the historical TLS-to-plain fixtures across JDK providers without changing frozen cases or expected
  answers.
- Preserved the frozen parity corpus's recorded byte identity across Git checkouts without changing its semantic
  content or checksum.
- Canonicalized the macOS JUnit temporary-directory fixture without weakening production export symlink rejection.

RC2 passes local verification but remains remotely unqualified until the draft-PR workflow set completes. RC1 is a
frozen local-only checkpoint and will not be moved or pushed.

## [0.1.0-rc.1]

Public alpha candidate. APIs and behavior may change before a stable release. The local full-reactor gate passed,
and this version has not been published remotely.

### Added

- Java 21 deterministic incident snapshots, throwable traversal, evidence extraction, diagnosis, and explicit
  unknown fallback.
- Servlet-based Spring MVC capture for exceptions that escape the request chain while preserving the original
  exception and host HTTP behavior.
- Mandatory secret and PII sanitization for StackMate-controlled console and export content.
- SHA-256 incident fingerprints and bounded process-local duplicate tracking.
- Bounded English `box` and `compact` console renderers.
- Optional atomic local export containing `incident.json`, `diagnosis.md`, `stacktrace.txt`, and `agent-prompt.md`.
- Strict typed configuration with 25 public `stackmate.*` properties.
- Production-profile guard that blocks `prod` and `production` by default unless
  `stackmate.safety.allow-in-production=true` is explicitly set.
- Public Maven coordinates for `stackmate-parent`, `stackmate-core`,
  `stackmate-spring-boot-autoconfigure`, and `stackmate-spring-boot-starter`.

### Release boundary

- The public product supports Java 21, Spring Boot 4.1.0, Spring Framework 7.0.8, and Servlet/Spring MVC.
- LLM, inference, models, Jlama, local starters, samples, E2E harnesses, benchmarks, and research artifacts are not
  part of the public release.
- No request body, response body, query-value, cookie, authorization, or arbitrary header capture API is exposed.
- No model download, cloud call, telemetry, incident upload, or remote service is present in the public runtime.

### Verification status

- Public-release install: passed for `0.1.0-rc.1`.
- External consumer: 6/6 contract checks and 1/1 packaged-process check passed.
- Full reactor: 543 Surefire plus 5 Failsafe tests passed; failures/errors/skips `0/0/0`.
- Two clean public-release builds produced 18 byte-identical compared payload files with zero differences.
- Local RC bundle assembled and audited; manifest SHA-256
  `108dbbb31f207fa1db68355ae536c63572468293dddfc79ffcc3a0557447b3da`.
- Maven Central publication, remote release creation, and GPG signing: not performed.

### Known limitations

- This is an alpha, not a production-supported release or security certification.
- Only exceptions escaping the servlet filter chain are visible; startup, logged-only, and resolved MVC failures are
  outside the capture boundary.
- Compatibility is locally verified only on Windows 11 with Amazon Corretto 21.0.12.1, Spring Boot 4.1.0, Spring
  Framework 7.0.8, and Spring MVC.
- Linux, macOS, containers, other Java distributions, and other Spring Boot 4.1.x patches are unverified.
- Spring Boot 3, WebFlux, GraalVM native image, and production deployment are unsupported for this alpha.
