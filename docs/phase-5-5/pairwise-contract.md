# Phase 5.5 pairwise contract

The model receives one candidate at a time and classifies only its relationship
to supplied trusted evidence. Sibling candidates, list position, family ID,
persistent candidate ID, expected label, final limitation, and diagnostic prose
are absent from model input.

| Token | Token ID | Meaning |
|---|---:|---|
| `0` | 15 | `CONTRADICTED` |
| `1` | 16 | `SUPPORTED` |
| `2` | 17 | `UNKNOWN` |

Definitions:

- `SUPPORTED`: trusted evidence directly entails the candidate.
- `CONTRADICTED`: trusted evidence directly rules out the candidate.
- `UNKNOWN`: evidence neither supports nor contradicts the candidate enough.

Plausibility and absence of contradiction are not support. An unselected
listwise candidate is not automatically contradicted. Checks require a selected
supported cause or direct check-specific evidence; “reasonable to inspect” is
not sufficient.

Canonical model input uses typed delimiters:

```text
<CANDIDATE_TYPE>CAUSE or CHECK</CANDIDATE_TYPE>
<TRUSTED_EVIDENCE>bounded sanitized facts</TRUSTED_EVIDENCE>
<NEGATIVE_EVIDENCE>explicit candidate-targeted facts or NONE</NEGATIVE_EVIDENCE>
<CANDIDATE>one untrusted candidate text</CANDIDATE>
```

Training, if ever authorized, appends exactly one frozen label token to the
official assistant-generation prefix and masks every prompt token. It does not
train the chat end token, JSON, IDs, indexes, explanations, or hidden reasoning.
Inference P1 would read the next-token logits at the last non-padding prompt
position and apply restricted softmax only over token IDs 15, 16, and 17.

Frozen identities:

```text
Base revision      15852e8c16360a2fea060d615a32b45270f8a8fc
Tokenizer          Qwen2Tokenizer
Chat template      273d8e0e683b885071fb17e08d71e5f2a5ddfb5309756181681de4f5a1822d80
Prompt             93515b517ff08c5bcf92764936e62a4c8709652fe0ff14152fbe0f6fc3a96517
Label mapping      98b223427fc6be46cedbe74fd3e10c9696900fba59f032387d63288925e2de76
```

The contract is technically valid. Dataset feasibility is a separate gate.
