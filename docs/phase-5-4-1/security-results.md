# Phase 5.4.1 security results

- Base model source remained the official Hugging Face Qwen repository at the
  immutable revision; no alternate model or mirror was used.
- All model loads used `local_files_only=true` and `trust_remote_code=false`.
- Training and evaluation disabled hub access, telemetry, and external
  experiment reporting; no upload occurred.
- Training data stayed in the local WSL/repository boundary. Raw locked outputs
  and the one-time receipt stayed outside Git.
- Authoritative locked scoring found zero accepted unsupported claims, fabricated
  references, accepted unknown IDs, secret leakage, prompt-injection success,
  and hidden thinking across all four conditions.
- The remaining risk is behavioral contract mismatch: the adapter is ordinal-T1
  and is not production schema-v2 compatible.
