# Phase 5 progress

Updated: 2026-08-02

## Objective

Qualify exactly `Qwen3.5-2B` Q4_K_M as an English-only, explanation-only local model. A production adapter,
packaged Spring integration, and any future provisioning decision remain conditional on the preceding gates.

## Baseline

- Phase 4 branch: `phase-4-english-small-model-tournament`
- Phase 4 commit: `7f710e8d265c11583403b6e23de64765df8b251d`
- Local annotated tag: `phase-4-complete`
- Phase 5 branch: `phase-5-qwen35-2b-qualification`
- Initial working tree: clean
- Remote repositories: none configured

## Completed

- Read Phase 4 product, architecture, benchmark, inference, security, and ADR documentation.
- Reproduced the normal Java 21 build: 311 Surefire tests and 11 Failsafe tests, with no failures, errors, or skips.
- Inspected all 11 reactor projects and their runtime dependency trees. The deterministic starter still has no
  inference-engine dependency and `stackmate-core` still has no runtime dependency.
- Started the packaged sample and captured real deterministic, redaction, duplicate, unknown-error, and disabled
  behavior. Every failure endpoint retained Spring Boot's HTTP 500 response and the disabled profile emitted no
  StackMate diagnosis.
- Reproduced the Phase 4 Qwen control screen: 20/20 valid structured outputs and 20/20 exact cause/check selections,
  with zero accepted safety violations.
- Revalidated the ignored Qwen GGUF artifact and pinned the current official llama.cpp `b10223` Windows CPU archive.
- Reviewed current official model metadata, runtime releases, and published GGUF parser advisories.

## Verification results

| Check | Result |
|---|---|
| `clean verify` | PASS, 11/11 projects |
| Surefire | 311 tests, 0 failures/errors/skips |
| Failsafe | 11 tests, 0 failures/errors/skips |
| Maven reported time | 57.137 s |
| Core line coverage | 96.46% |
| Core branch coverage | 87.14% |
| Packaged deterministic endpoints | HTTP 500 preserved |
| Disabled endpoint | HTTP 500 preserved, 0 StackMate blocks |
| Worker after verification | none observed |
| Qwen control screen | PASS, 20/20 valid and exact selections |

## Command discrepancies and failed attempts

- The first dependency-tree command left an output filename containing spaces unquoted. Maven interpreted the final
  token as a lifecycle phase and failed. The corrected quoted property completed for all modules.
- The first Hugging Face metadata request timed out after 30 seconds. A bounded `curl` retry against the official API
  succeeded.
- A metadata-only `llama-completion` invocation with `--predict 0` exited non-zero. A one-token invocation produced
  the required GGUF metadata without changing the artifact.
- A hardware query initially used the shell's Java 17 because `JAVA_HOME` was not set inside that command. It was
  repeated with the Phase 5 Java 21 environment; no Maven verification was performed with Java 17.

## Current decisions

- Use official llama.cpp `b10223` (`11924d4c17abc27383376a1ac6a24fa3e36c1c0c`) for Phase 5 qualification.
  It supersedes the Phase 4 `b10221` control runtime and carries the current official Windows CPU artifact digest.
- Keep the Qwen artifact experimental: it is a third-party GGUF conversion pinned by bytes and SHA-256, not an
  official Qwen-distributed GGUF.
- Freeze and commit the 20-case blind corpus before exposing any blind case to the model.
- Do not change production code before Gate A produces independent evidence.

## Remaining work

1. Freeze and commit the independent 20-case blind corpus.
2. Run and manually review the combined 100-case Gate A qualification.
3. Proceed to persistent-worker qualification only if every Gate A threshold passes.
4. Proceed to a production adapter and packaged Spring verification only through the dependent gates.
5. Publish final benchmark, threat-model, CI, and product documentation without overwriting historical reports.

## Current risks

- The GGUF reports `qwen35` and Q4_K_M-compatible metadata, but llama.cpp notes unused MTP tensors. The text-only
  qualification must establish whether that compatibility detail affects output quality.
- Phase 4 used one process per case. It is not evidence for persistent-worker stability, memory bounds, cleanup, or
  multi-request framing.
- Only Windows x86_64 has been executed locally. Linux and macOS remain unverified until an actual runner executes
  their paths.
- The same reviewer may need to perform both manual-review passes on this machine. If so, the lack of reviewer
  independence will be reported explicitly rather than hidden.
