# Phase 5.3 chat-template parity

Date: 2026-08-22

## Static template result

The official local tokenizer rendered P0 and P3 over all 40 parity cases. Its
`apply_chat_template(..., add_generation_prompt=true, enable_thinking=false)`
output was compared with the historical manually assembled llama.cpp Qwen envelope.

| Check | Result |
|---|---:|
| Rendered text equality | 80/80 |
| Official-tokenizer token-ID equality | 80/80 |
| Maximum prompt tokens | 1,296 |
| Duplicate BOS | 0 |
| Prompt truncation at context 2,048 | 0 |

System role placement, user delimiters, non-thinking assistant prefix, and special
token boundaries therefore agree before prompt-file transport.

## Actual llama.cpp prompt-file behavior

Pinned llama.cpp `--verbose-prompt` showed a one-token transport difference in the
historical path for every case:

```text
Expected official final token: 271  (double newline)
Observed llama.cpp final token: 198  (single newline)
Location: final assistant non-thinking prefix token
```

All preceding token IDs and token counts matched. Appending one transport-only
newline to the prompt file restored exact official token IDs in 80/80 cases.

This difference changes raw greedy output frequently: only 18/80 outputs were
byte-identical before and after compensation. It does not explain the aggregate
performance discrepancy. P3 remained 40/40 validator-valid and 38/40 cause/check
in both runs; corrected P0 changed from 35/40 validator-valid and 36/40
cause/check to 32/40 and 35/40.

## Conclusion

The chat template is semantically aligned, and the prompt-file newline transport
defect is real but not the cause of the baseline collapse. Phase 5.3 parity uses
the compensated 80/80 token-identical llama.cpp path for controlled comparisons.
