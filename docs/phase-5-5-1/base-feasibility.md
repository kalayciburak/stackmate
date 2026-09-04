# Phase 5.5.1 base feasibility

Status: `NOT_RUN`.

Gate C requires a valid frozen binary TRAIN/VALIDATION corpus. The conservative
candidate audit leaves fewer than 15 defensible `SUPPORTED` VALIDATION pairs, so
Gate B closes before model loading. Running restricted logits over forced labels
would measure annotation noise rather than binary evidence support.

| Required metric | Result |
|---|---:|
| Valid labels | NOT_MEASURED |
| SUPPORTED precision / recall | NOT_MEASURED |
| NOT_SUPPORTED precision / recall | NOT_MEASURED |
| Macro F1 | NOT_MEASURED |
| False support | NOT_MEASURED |
| Family cause / check / joint | NOT_MEASURED |
| Strict restraint | NOT_MEASURED |
| Order invariance | NOT_MEASURED |
| Prompt injection | NOT_MEASURED |
| Latency | NOT_MEASURED |

The official checkpoint remains present and hash-bound outside Git, but no Phase
5.5.1 model weight, forward pass or next-token logit was loaded.
