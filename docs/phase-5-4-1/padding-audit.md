# Tokenization and padding audit

The audit used the official tokenizer from the immutable Qwen3.5-2B snapshot
and the exact T1 development files.

| Split | Rows | Mean input | P95 input | Max input | Mean output | Max output | Max total |
|---|---:|---:|---:|---:|---:|---:|---:|
| TRAIN | 140 | 801.77 | 978 | 1,019 | 20.94 | 24 | 1,039 |
| VALIDATION | 40 | 815.05 | 993 | 1,015 | 21.65 | 23 | 1,035 |

Results:

- truncation count: 0;
- unknown token count: 0;
- assistant boundary: verified for every row;
- prompt labels: masked and assistant targets: unmasked;
- duplicate BOS: false; the tokenizer has no BOS ID;
- EOS ID: 248046; PAD ID: 248044; padding side: right;
- chat-template SHA-256: `273d8e0e683b885071fb17e08d71e5f2a5ddfb5309756181681de4f5a1822d80`;
- audit report SHA-256: `986da692bfc4b3bd00422fb98a33321a68f027986a8277bb580a09e661c64ee1`.

The training collator uses dynamic per-batch padding and the configured batch
size is one. Fixed padding has therefore not been proven and no sequence-cap
mitigation was used. The 2,048-token bound is retained unchanged because the
longest audited sequence is 1,039 tokens.
