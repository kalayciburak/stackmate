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
- A frozen independent 80-case V2 corpus with a 20-case holdout split, 67.5% executable runtime/integration cases,
  restraint, sanitization, fingerprint, and latency metrics.
- Maven Failsafe packaged-process verification for deterministic, disabled, Turkish, and managed fake-worker modes.
- Explanation-only inference schema version 2, separate English/Turkish prompts, and an evidence-aware trigger policy
  that skips exact and insufficient diagnoses by default.
- Version-2 child protocol hardening, strict SafeTensors range/shape checks, adversarial sanitizer/path/lifecycle tests,
  process-level startup measurement, request-path JMH, and an actual console gallery.
- Completion-audit coverage for read-only/long/Unicode model paths, unexpected model artifacts, repeated redaction,
  child stdout contamination, partial responses, and a real-model packaged lifecycle that accepts safe fallback.
- A test-scoped Phase 3 llama.cpp screening harness with immutable model/runtime inputs, strict per-request JSON
  schemas, hidden-thinking rejection, unchanged semantic validation, and bilingual Qwen3.5 2B/4B measurements.

### Security

- Redaction and output-boundary controls for common credentials, tokens, personal identifiers, terminal controls,
  and configured sensitive patterns.
- Model-free deterministic fallback when optional inference is absent, invalid, unavailable, or times out.
- Sanitized bounded debug reasons, exact model metadata validation, symbolic-link rejection, strict protocol versioning,
  and packaged orphan-process assertions.
- Removed obsolete packaged prompts that still described model-authored source locations; only schema-v2 English and
  Turkish resources remain.
- Verified SHA-256 and size for the two ignored feasibility GGUF files and the official llama.cpp Windows archive;
  rejected java-llama.cpp's older embedded runtime and excluded llama.cpp RPC from the experiment.

### Known limitations

- `0.1.0-SNAPSHOT` is unreleased and under active development; APIs and behavior may change.
- No general accuracy, production-readiness, platform-support, or support-SLA claim is made.
- Phase 1 Qwen2.5-0.5B-Instruct/Jlama results remain a historical `EXPERIMENTAL` baseline. Under the reduced Phase 2
  contract, Qwen2.5-0.5B-Instruct and Qwen2.5-Coder-0.5B-Instruct are `NOT_VIABLE`: all four 10-case
  English/Turkish screens produced zero valid output and missed latency/memory targets.
- Phase 3 Qwen3.5-2B and Qwen3.5-4B Q4_K_M configurations are also `NOT_VIABLE`. The 2B model failed Turkish
  selection/usefulness; the 4B model failed the mandatory 85% cause/check selection gate. Gates B and C were not
  run, so there is no production llama.cpp adapter or automatic provisioning.
