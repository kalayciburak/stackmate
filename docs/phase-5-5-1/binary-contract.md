# Phase 5.5.1 binary evidence-support contract

The model receives exactly one cause or check candidate and only the trusted
evidence supplied for that candidate. Its output is a binary product decision,
not an open-world truth assertion.

| Token | Token ID | Meaning |
|---|---:|---|
| `0` | 15 | `NOT_SUPPORTED` |
| `1` | 16 | `SUPPORTED` |

`SUPPORTED` means that the supplied evidence directly justifies selecting the
candidate. `NOT_SUPPORTED` means that it does not; the candidate may still be
plausible or true in another context. Generic advice, absent evidence and
ambiguity are not support.

The model-facing input contains candidate type, typed evidence values, an
explicit negative-evidence section when available, and one escaped candidate
text. It must contain no sibling candidate, candidate order/index, persistent
candidate or family ID, expected label/disposition, diagnostic prose target,
source code, request body, header, secret or private path.

The model emits exactly one plain label token. No whitespace, punctuation, JSON,
Markdown, explanation, confidence, hidden reasoning or repair is allowed. Any
other token sequence is counted invalid and mapped to `NOT_SUPPORTED` for
fail-safe aggregation.

Frozen identities:

```text
Base revision      15852e8c16360a2fea060d615a32b45270f8a8fc
Tokenizer          Qwen2Tokenizer
Chat template      273d8e0e683b885071fb17e08d71e5f2a5ddfb5309756181681de4f5a1822d80
Prompt             fc4c3569a17ab5b902b88c6105f5b0d7587087eabd129b779395c7400ce01ddb
Label mapping      fa98365a55f91f7902890b4fad5b034d945f9bacaa43d1ec90d70a59af3873d8
```

Restricted-logit inference will compare only token IDs 15 and 16 at the first
assistant-generation position. Scores are not called calibrated before the
validation-only calibration gate.
