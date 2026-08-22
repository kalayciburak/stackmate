# Phase 5.3 tokenization audit

Date: 2026-08-22

The exact official tokenizer and chat template were loaded locally at revision
`15852e8c16360a2fea060d615a32b45270f8a8fc`, with
`trust_remote_code=false` and network access disabled.

| Split / measure | Average | P95 | Maximum |
|---|---:|---:|---:|
| TRAIN input tokens | 801.77 | 978 | 1,019 |
| TRAIN target tokens | 20.94 | 23 | 24 |
| TRAIN total tokens | 822.71 | 1,001 | 1,039 |
| VALIDATION input tokens | 815.05 | 993 | 1,015 |
| VALIDATION target tokens | 21.65 | 23 | 23 |
| VALIDATION total tokens | 836.70 | 1,016 | 1,035 |

- Sequence limit: 2,048.
- Truncations: 0.
- Unknown tokens: 0.
- Assistant boundary: verified for 180/180 rows.
- Prompt tokens masked / target tokens unmasked: 144,850 / 3,798.
- Duplicated BOS: none; the tokenizer has no BOS token.
- Chat-template SHA-256:
  `273d8e0e683b885071fb17e08d71e5f2a5ddfb5309756181681de4f5a1822d80`.

Machine report SHA-256:
`986da692bfc4b3bd00422fb98a33321a68f027986a8277bb580a09e661c64ee1`.
