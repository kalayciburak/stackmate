# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project intends to follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) after its first release.

## [Unreleased]

### Changed

- Phase 5.5.2 replaces compound/listwise candidates with a reviewed 60-entry
  atomic ontology and a 60/25-family contrastive binary dataset. Dataset and
  shortcut gates pass, but official BF16 base feasibility fails: validation
  macro F1 is 45.20%, support recall is 12.90%, three unsupported attacks are
  accepted, and six pair predictions change with batch order. Training,
  calibration and exposed diagnostics remain unrun.
- Phase 5.5.1 freezes an offline two-label, one-token-output
  `SUPPORTED`/`NOT_SUPPORTED` classification contract, then stops at dataset
  validity. A conservative audit
  removes compound checks, probe placeholders, hedged causes and unselected
  causes carrying direct positive evidence; only 12 defensible `SUPPORTED`
  validation pairs remain, below the required 15. Base inference, training,
  calibration and exposed diagnostics are therefore unrun.
- Phase 5.5 freezes a one-token pairwise entailment contract and audits the
  Phase 5.3 real-world source without changing frozen data. TRAIN yields
  102 `SUPPORTED`, 0 `CONTRADICTED`, and 104 `UNKNOWN` native pairs;
  VALIDATION yields 18/0/36. Because listwise omission is not contradiction,
  Gate B fails and base inference, training, calibration, exposed diagnostics,
  merge, and GGUF work remain unrun.
- Phase 5.4.3 freezes a genuinely new 30-family T1 final corpus and runs D0,
  official-base B0, and checkpoint-18 A1 exactly once. A1 improves joint family
  accuracy from B0's 3/30 to 16/30, but deterministic D0 reaches 15/30 with
  stronger restraint; A1 fails safety, quality, and practical-value gates, so
  fine-tuning value is not justified and merge/GGUF remains unrun.
- Phase 5.4 verified the frozen corpus and official base, then stopped at the
  precision gate: BF16 LoRA smoke was finite and reloadable but exceeded the
  reserved-VRAM safety headroom, while NF4 materially degraded restraint and
  introduced fallback/index failures. No real run, final qualification, merge or
  GGUF export was performed; the Phase 5.4 adapter is `NOT_VIABLE`.
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

- Phase 5.5.2 controlled atomic development families, predicate-derived pair
  reviews, independent semantic review, S0-S4 plus evidence-shape shortcut
  baselines, offline tokenization audit and direct next-token-logit base report.
  No adapter, model artifact or production runtime dependency was added.
- Phase 5.5.1 binary prompt/token audit, family-level candidate audit,
  ambiguity/security coverage evidence and explicit blocked-gate reports. No
  binary dataset rows, model output, adapter or product dependency were added.
- Phase 5.5 pairwise label-token audit, contract prompt, conservative source
  auditor, leakage evidence, regression tests, and explicit blocked-gate reports.
  No pairwise dataset rows, adapter, model output, or product dependency were added.
- Phase 5.4.3 exposed-family inventory, real-runtime recipes, task-level novelty
  audit, two-pass labels, separated generation/scoring artifacts, formal T1
  schema, exactly-once runner, scorer-only defect repair, and fresh-final result
  reports. No model or prediction artifact is committed.
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

- Phase 5.4.3 binds generation to an annotated freeze tag, clean HEAD, base and
  adapter hashes, prompt/schema/config hashes, label-free messages, and one
  receipt per system. A1 had zero secret leakage, hidden thinking, unknown
  fields, trailing tokens, or out-of-bounds indexes, but failed the zero attack
  and contradictory-restraint gates.
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
## Unreleased

- Added Phase 5.4.2 T1/schema-v2 contract recovery evidence and an authoritative
  cross-language ordinal T1 scorer.
- Preserved and forensically classified the Phase 5.4.1 locked outputs without
  regeneration; fresh-final qualification remains blocked pending an independent
  corpus.
