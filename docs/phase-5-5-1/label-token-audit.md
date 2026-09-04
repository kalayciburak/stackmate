# Phase 5.5.1 label-token audit

The exact official tokenizer was loaded offline with `local_files_only=true`,
`trust_remote_code=false`, and telemetry disabled. Model weights were not loaded.

```text
0 -> token 15 -> NOT_SUPPORTED
1 -> token 16 -> SUPPORTED
```

Each plain digit encodes to exactly one token and decodes to itself. Leading
space and leading newline forms encode to two tokens, so neither is an allowed
output. Across three normal/adversarial contexts, each assistant suffix was
exactly:

```text
label token, EOS 248046, newline 198
```

The label remained the first token after the official assistant generation
prefix. Prompt lengths in these contract probes were 234–241 tokens. Training,
if authorized, masks the entire prompt and both termination tokens so only the
single label token contributes to target loss.

```text
Prompt SHA-256  fc4c3569a17ab5b902b88c6105f5b0d7587087eabd129b779395c7400ce01ddb
Mapping SHA-256 fa98365a55f91f7902890b4fad5b034d945f9bacaa43d1ec90d70a59af3873d8
Report SHA-256  da03c29aba32416ad81fa14413861751ea58f1a5c9b7d2d9c1dab150128a962e
```

Targeted contract verification passed 5/5 tests. The report contains no machine
path and records zero network access.
