# ADR 0016: Pinned llama.cpp screening runtime

- Status: Accepted for Gate A screening
- Date: 2026-08-01

## Context

Qwen3.5 requires a current llama.cpp implementation. The published
`kherud/java-llama.cpp` source and releases inspected for this phase pin llama.cpp
`b4916`, which predates Qwen3.5 support and relevant GGUF security fixes. Loading that
artifact would violate the Phase 3 security floor.

## Decision

Use official llama.cpp release `b10219`, commit
`c629da565c80b0b17fac6262acdca4d772e745d8`, for isolated Gate A screening. On the
actual Windows x86_64 machine, use the official CPU archive
`llama-b10219-bin-win-cpu-x64.zip`, whose verified SHA-256 is
`5f3fc78e61d7402f7051c3580159c8a12ff6cb98912e42f4272932e1afb7f882`.

Prompts are supplied through files, output is constrained by JSON schema, reasoning
is explicitly disabled, and Phase 2 semantic validation remains the final trust
boundary. No RPC backend, localhost server, or remote inference endpoint is used.

## Security basis

`b10219` is newer than the relevant published GGUF parsing fixes, including the
`b8146` fix for `GHSA-3p4r-fq3f-q74v`. The critical RPC advisory remains an upstream
residual, but the RPC backend is outside the selected build and execution path.
Checksums, immutable revisions, and expected sizes are verified before native parsing.

## Consequences

- This decision pins a screening executable; it does not select a production Java
  adapter or managed-worker architecture.
- Only Windows x86_64 is locally verified. Published Linux and macOS assets remain
  untested here.
- A source-built production worker requires a separate Gate B ADR and artifact
  provenance record.
- Native model parsing remains a residual supply-chain and memory-safety risk.
