# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project intends to follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) after its first release.

## [Unreleased]

### Changed

- Phase 5.3 bounds the Transformers/llama.cpp discrepancy, selects a future
  ordinal selection-only contract, and freezes a reviewed 120-family controlled
  real-world corpus with zero prohibited leakage. The unchanged F1 adapter reaches
  only 18/40 valid T1 outputs and 0/22 strict restraints on the new validation
  split, so one future bounded run is justified but not executed.
- Phase 5.2 freezes a 1,000-row deterministic family-split alignment dataset and
  a new 30-case post-training challenge, but ends `PARTIAL/BLOCKED` before adapter
  training because the official base checkpoint is absent and model download is
  out of scope. Qwen3.5-2B remains `EXPERIMENTAL`.
- Phase 5.2 was resumed through controlled official checkpoint acquisition. The
  exact Qwen3.5-2B checkpoint was verified, F1/F2 QLoRA runs completed, F1 was
  validation-only selected and locked, and the 70-case locked comparison ran once.
  A1 reached 68/70 valid structured outputs, 44/70 cause accuracy, and 45/70
  check accuracy; mandatory locked gates failed, so no full confirmation, merge,
  GGUF export, or production readiness claim was made.
- Phase 5.1 runs a controlled P0-P3 prompt ablation for the pinned Qwen3.5-2B artifact. The selected P3 prompt fixes
  all 11 known abstentions and removes generic fallback misuse, but remains `EXPERIMENTAL`: V2 locked holdout is
  16/20 and the final manual pass finds one accepted unsupported claim.
- The output validator now requires an explicit limitation for empty selections and rejects selections that
  contradict an insufficient-evidence claim. A broader lexical claim checker remains research-only after excessive
  false/over-conservative rejections.
- Phase 5 replaces the tournament's strict parameter cap with measured resource budgets, while retaining mandatory
  semantic, safety, isolation, and lifecycle gates.
- The exact pinned Qwen3.5-2B Q4_K_M candidate remains `NOT_VIABLE` after full 80-case V2 plus 20-case blind
  qualification: structural and selection metrics passed, but strict restraint and manual semantic safety did not.

- Phase 4 makes StackMate-generated runtime diagnostics and model evaluation English-only while retaining the full
  Turkish usage/development guide in `README.tr.md`.
- Local-model readiness now requires the smallest eligible candidate to pass strict aggregate and holdout gates
  under a hard limit of 2,000,000,000 total parameters; marketing names are not used as the parameter count.
- StackMate-generated runtime diagnostics, inference prompts, model explanations, setup messages, and incident-export
  headings are now English only. `README.tr.md` remains the complete Turkish documentation.
- The pre-release inference schema and rendering APIs no longer carry one-value language selectors.
- Unknown `stackmate.*` configuration is rejected to make stale settings visible.

### Removed

- Removed `stackmate.language`, the Turkish sample profile, Turkish runtime renderer/prompt resources, Turkish
  inference benchmark mode, and tests whose only purpose was runtime localization.

### Added

- Phase 5.3 parity, failure-taxonomy, contract-ablation, real-world corpus,
  two-pass review, leakage, tokenization, F1 diagnostic, security, and future-run
  decision evidence. PostgreSQL/Testcontainers fixtures remain explicit benchmark
  labs and add no product runtime dependency.
- An isolated, locked WSL2 PyTorch/Transformers/PEFT/bitsandbytes research
  environment; deterministic alignment data generator; locked-corpus leakage
  audit; offline preflight; F1/F2 configs; and fail-closed evaluation manifests.
- Versioned P0-P3 prompt resources, a frozen 40/20/40 split, prompt-ablation and validator-replay labs, an eight-case
  adversarial prompt suite, and a non-adopted clause-level evidence-grounding experiment.
- Phase 5.1 baseline, prompt audit, ablation, selection, locked-result, manual-review, security, and partial-decision
  reports. Gate C is explicitly skipped after locked holdout failure.
- A frozen 20-case Phase 5 blind qualification corpus with 18 real runtime/integration captures, independent
  ground truth, pre-model manifest hashing, and input/candidate validation.
- An explicit 100-case llama.cpp qualification lab pinned to official `b10223`, plus generated machine-readable
  results and two-pass manual usefulness/semantic-safety review.
- Phase 5 provenance, qualification, negative gate decision, and native/GGUF research threat-model documentation.

- A frozen 20-case English V3 tournament corpus with a 10-case holdout split, 16 real runtime/integration captures,
  independent ground truth, pinned candidate manifest, and unchanged schema-v2 semantic validation.
- An isolated llama.cpp `b10221` tournament harness for SmolLM2-360M-Instruct, Qwen3-0.6B,
  Qwen2.5-1.5B-Instruct, and an ineligible Qwen3.5-2B historical control. It records strict validity, aggregate and
  holdout selections, manual usefulness, safety counters, generation latency, and sampled working set.
- Java 21 multi-module Maven project with a Maven 3.9.16 wrapper and Apache License 2.0 metadata.
- Spring-independent deterministic incident capture, sanitization, evidence, diagnosis, fingerprinting, and bounded
  duplicate tracking.
- Evidence-backed analyzers for common Java, Spring, web, persistence, security, connection, concurrency, timeout,
  and resource failure categories, with an explicit unknown fallback.
- Spring Boot 4.1 servlet auto-configuration, typed settings, production-profile guard, extension points, and
  non-mutating exception observation.
- English bounded console rendering, compact and box styles, and an executable sample application.
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
- Maven Failsafe packaged-process verification for deterministic, disabled, and managed fake-worker modes.
- Explanation-only inference schema version 2, an English prompt, and an evidence-aware trigger policy
  that skips exact and insufficient diagnoses by default.
- Version-2 child protocol hardening, strict SafeTensors range/shape checks, adversarial sanitizer/path/lifecycle tests,
  process-level startup measurement, request-path JMH, and an actual console gallery.
- Completion-audit coverage for read-only/long/Unicode model paths, unexpected model artifacts, repeated redaction,
  child stdout contamination, partial responses, and a real-model packaged lifecycle that accepts safe fallback.
- A test-scoped Phase 3 llama.cpp screening harness with immutable model/runtime inputs, strict per-request JSON
  schemas, hidden-thinking rejection, unchanged semantic validation, and bilingual Qwen3.5 2B/4B measurements.

### Security

- English output validation now rejects letters outside the Unicode Latin script. A real non-English fragment from
  the COMPACT screen was rejected, repaired once, and never exposed as trusted output.
- Phase 4 model and official Windows llama.cpp artifacts were pinned by immutable revisions, expected sizes, and
  SHA-256 before isolated execution. No GGUF/native artifact enters a production module or normal CI.
- Corrected the research harness so a rejected initial language/fabrication issue is never counted as accepted when
  a later bounded repair succeeds; acceptance counters now inspect only the final validated response.
- Redaction and output-boundary controls for common credentials, tokens, personal identifiers, terminal controls,
  and configured sensitive patterns.
- Model-free deterministic fallback when optional inference is absent, invalid, unavailable, or times out.
- Sanitized bounded debug reasons, exact model metadata validation, symbolic-link rejection, strict protocol versioning,
  and packaged orphan-process assertions.
- Removed obsolete packaged prompts that still described model-authored source locations; only the schema-v2 English
  resources remain.
- Verified SHA-256 and size for the two ignored feasibility GGUF files and the official llama.cpp Windows archive;
  rejected java-llama.cpp's older embedded runtime and excluded llama.cpp RPC from the experiment.

### Known limitations

- The historical Phase 5.2 blocked report predates checkpoint acquisition. The
  resumed F1/F2 adapter remains `EXPERIMENTAL` because locked qualification failed;
  no merged model or fine-tuned GGUF was created. Transformers 5.14.1 still does
  not support loading the available third-party `qwen35` GGUF architecture for
  training.

- Phase 5.1 is `PARTIAL`, Qwen3.5-2B is `EXPERIMENTAL`, and no persistent worker, production llama.cpp adapter,
  provisioning, or packaged model was implemented. The recommended next local-model research phase is task-specific
  fine-tuning, not additional prompt variants.
- No eligible Phase 4 sub-2B model passed every mandatory tournament gate. Gate C full benchmarking and persistent
  worker validation were not run, and no production llama.cpp adapter or provisioning system was implemented.
- `0.1.0-SNAPSHOT` is unreleased and under active development; APIs and behavior may change.
- No general accuracy, production-readiness, platform-support, or support-SLA claim is made.
- Phase 1 Qwen2.5-0.5B-Instruct/Jlama results remain a historical `EXPERIMENTAL` baseline. Under the reduced Phase 2
  contract, Qwen2.5-0.5B-Instruct and Qwen2.5-Coder-0.5B-Instruct are `NOT_VIABLE`: all four 10-case
  English/Turkish screens produced zero valid output and missed latency/memory targets.
- Phase 3 Qwen3.5-2B and Qwen3.5-4B Q4_K_M configurations are also `NOT_VIABLE`. The 2B model failed Turkish
  selection/usefulness; the 4B model failed the mandatory 85% cause/check selection gate. Gates B and C were not
  run, so there is no production llama.cpp adapter or automatic provisioning.
