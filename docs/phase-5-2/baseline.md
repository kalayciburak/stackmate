# Phase 5.2 baseline

Date: 2026-08-02

## Repository boundary

| Item | Reproduced value |
|---|---|
| Reviewed Phase 5.1 commit | `9d1b1af10d9fcb1c39da2ee882997bed8f577a45` |
| Local completion tag | `phase-5-1-complete` |
| Phase 5.2 branch | `phase-5-2-task-specific-alignment` |
| Initial working tree | clean |
| Remote | none configured |
| Historical Phase 5.1 reports | tracked and unchanged |

The reviewed commit exactly matches the expected Phase 5.1 completion commit. No
history was rewritten and no remote operation was performed.

## Clean Java verification

The command-local Java 21 invocation was:

```powershell
$env:JAVA_HOME='C:\Users\Burak\.jdks\corretto-21.0.12'
.\mvnw clean verify
```

| Measurement | Expected | Reproduced |
|---|---:|---:|
| Reactor modules | 11 | 11 |
| Surefire | 346 | 346 |
| Failsafe | 11 | 11 |
| Total | 357 | 357 |
| Failures / errors / skips | 0 / 0 / 0 | 0 / 0 / 0 |

The build completed with `BUILD SUCCESS` in 01:26 Maven time. The normal reactor
required no Python environment, model file, network access, or training dependency.

## Frozen Phase 5.1 controls

| Control | Value |
|---|---|
| Selected prompt | `P3`, `phase5.1-p3-v1` |
| Canonical stripped-text prompt SHA-256 | `d5117da41bd8af64982035fb75dbac91968249ea48fd02f120c532755074bf7f` |
| Checked-out file-byte SHA-256 | `05c601c67a5b70b554ddf6c0ff3d49980c8104b55aebe9ab3e3433be30372f2f` |
| Prompt-ablation split file SHA-256 | `147d9a9e7e312d02abbf113ed3fd255bd4a3ee7841413c488f36deb44c016a07` |

The two prompt hashes have different contracts. `PromptVariantManifest` strips the
UTF-8 resource before hashing and enforces the canonical value. The file-byte hash
includes checkout line-ending and terminal-whitespace representation. The resource
loaded by the Java harness passed its checksum test; this is not prompt drift.

Phase 5.1's curated P0/P3 metrics remain the authoritative historical baseline.
A bounded real-model development reproduction was completed before any training
weight was loaded.

## P0/P3 development reproduction

The frozen Phase 5.1 development harness requires all four primary prompts, so
the run produced `P0,P1,P2,P3 x 40` outputs. Only P0 and P3 are used here. It did
not load selection validation, holdout, blind, or the post-training challenge.

| Metric | Historical P0 | Reproduced P0 | Historical P3 | Reproduced P3 |
|---|---:|---:|---:|---:|
| Valid output | 40/40 | 38/40 | 40/40 | 40/40 |
| Cause accuracy | 39/40 | 39/40 | 36/40 | 36/40 |
| Check accuracy | 39/40 | 39/40 | 36/40 | 36/40 |
| Strict abstention | 1/4 | 1/4 | 4/4 | 4/4 |
| Explicit limitation | 1/4 | 1/4 | 4/4 | 4/4 |
| Generic fallback misuse | 1 | 1 | 0 | 0 |
| Median process latency | 5,250 ms | 5,256 ms | 5,340 ms | 5,174 ms |
| P95 process latency | 6,007 ms | 6,038 ms | 6,236 ms | 6,133 ms |

P3 reproduced exactly on validity, cause/check selection, and restraint. P0's
selection and restraint profile also reproduced, but the current production
validator rejected `JAVA-UNKNOWN-010` and `MVC-UNKNOWN-012` for empty selections
without limitations. Historical prompt ablation used the pre-hardening Phase 5
validator; commit `408800b` later added the general `MISSING_LIMITATION` rule.
This explains the 38/40 current-validity result and is not a prompt, model,
runtime, corpus, or artifact change.

| Locked input | SHA-256 |
|---|---|
| Q4_K_M GGUF | `57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8` |
| llama-completion | `4208fb923f6109143d47035aee315295c1c1ce87a062a8cfbbcb02a4d8a3fee2` |
| P0 canonical prompt | `3362afedc99b7c930a8423a33d011bb8b8a026d7072befef6041f6629b36fa03` |
| P3 canonical prompt | `d5117da41bd8af64982035fb75dbac91968249ea48fd02f120c532755074bf7f` |
| Split manifest | `147d9a9e7e312d02abbf113ed3fd255bd4a3ee7841413c488f36deb44c016a07` |
| Generated full report | `4ac7dbbf9d0db8ff2a3ada1652d7314f47a98218662d2ca72fefc4829c3bfebf` |

The canonical prompt hashes are calculated from the stripped resource text, as
the harness loads it. Windows raw file-byte hashes differ because of checkout
line endings; this is not prompt drift.

The Maven test completed in 14:15 with one test and zero
failures/errors/skips. The full generated report remains ignored under
`stackmate-benchmark/target/phase-5-2-baseline-reproduction/`; the committed JSON
contains only controls and aggregate metrics.

## Ordering deviation

The Phase 5.2 master sequence requested this real-model subset before dataset
generation. The deterministic generator and leakage audit were completed first,
after challenge freeze but before any training/model weight load. Dataset code
has no model-output input and no locked-corpus dependency, and no dataset choice
was changed after this reproduction. The deviation is procedural, preserved
here, and does not create adapter or locked-test contamination.
