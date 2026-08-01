# Phase 3 decision log

## D1 - Gate ordering is binding

Production integration is not authorized by evidence until Gate A passes. Provisioning is not implemented unless both
Gate A and Gate B pass. A Gate A failure ends model product work with documented `NOT_VIABLE` evidence while leaving
the deterministic product unchanged.

## D2 - Phase 2 inference authority remains unchanged

Category, root type, evidence strength, facts, locations, candidate causes, and candidate checks remain deterministic.
The Phase 3 model may emit only bounded explanation text, limitations, and selections of supplied IDs. Grammar
constrained generation supplements but never replaces schema and semantic validation.

## D3 - ADR history is append-only

The requested Phase 3 ADR topic numbers overlap existing historical ADR 0009 through ADR 0014. Phase 3 will allocate
the next unused numbers beginning at ADR 0015 and record the requested topic in each title. No historical ADR will be
renamed or overwritten.

## D4 - Research artifacts are not product artifacts

GGUF files and native screening binaries remain ignored, local research inputs. A feasibility artifact cannot enter a
production catalog without immutable provenance, size, SHA-256, architecture, quantization, license, and Gate A
evidence. No downloader will be built before that condition is met.

## D5 - Third-party GGUF is a feasibility input only

No official Qwen Q4_K_M repository was available at research time. Pinned Bartowski artifacts may be screened only
after byte-size and SHA-256 verification. Their cards do not cryptographically bind the conversion to the pinned
official Qwen revisions, so they cannot become a production `READY` catalog entry on this evidence alone.

## D6 - Use official llama.cpp b10219 for Gate A

The Windows x86_64 CPU archive from release `b10219`, commit
`c629da565c80b0b17fac6262acdca4d772e745d8`, passed local checksum and version verification. The published
java-llama.cpp binding was rejected because it embeds llama.cpp `b4916`, predating Qwen3.5 support and relevant GGUF
parser fixes.
