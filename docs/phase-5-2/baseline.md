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
A bounded real-model reproduction is recorded separately before training begins.
