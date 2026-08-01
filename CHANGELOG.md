# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project intends to follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) after its first release.

## [Unreleased]

### Added

- Java 21 multi-module Maven project with a Maven 3.9.16 wrapper and Apache License 2.0 metadata.
- Spring-independent deterministic incident capture, sanitization, evidence, diagnosis, fingerprinting, and bounded
  duplicate tracking.
- Evidence-backed analyzers for common Java, Spring, web, persistence, security, connection, concurrency, timeout,
  and resource failure categories, with an explicit unknown fallback.
- Spring Boot 4.1 servlet auto-configuration, typed settings, production-profile guard, extension points, and
  non-mutating exception observation.
- English and Turkish bounded console rendering, compact and box styles, and an executable sample application.
- A versioned 50-case deterministic benchmark corpus, report generation, safety counters, and JMH microbenchmarks.
- Engine-neutral local-inference contracts and an experimental, optional JVM-local inference integration with
  a managed child JVM, bounded execution, validated structured output, timeout/circuit-breaker fallback, and
  duplicate inference suppression. Model weights remain user-supplied and are not distributed.
- A bounded, sanitized incident-export contract for explicitly configured local export targets.
- CI verification on Linux and Windows, a macOS smoke build, and pull-request dependency review.
- Prompt-v2 raw/structured/deterministic model comparison over the 50-case English corpus and a five-case Turkish
  language sample, with machine-readable and human-readable generated reports.

### Security

- Redaction and output-boundary controls for common credentials, tokens, personal identifiers, terminal controls,
  and configured sensitive patterns.
- Model-free deterministic fallback when optional inference is absent, invalid, unavailable, or times out.

### Known limitations

- `0.1.0-SNAPSHOT` is unreleased and under active development; APIs and behavior may change.
- No general accuracy, production-readiness, platform-support, or support-SLA claim is made.
- Qwen2.5-0.5B-Instruct/Jlama inference is `EXPERIMENTAL`: structured input beats raw input, but current quality,
  latency, memory, and Turkish-language results do not pass the initial readiness gates.
