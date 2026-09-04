# Phase 5.5 performance

No model inference or training ran, so sequential latency, batched latency, peak
VRAM, parent memory, tokens per real pair, and pairs per second are `NOT_MEASURED`.

The source audit nevertheless bounds the native candidate fan-out:

| Split | Mean cause pairs/family | Mean check pairs/family | Mean total pairs/family |
|---|---:|---:|---:|
| TRAIN | 1.47 | 1.47 | 2.94 |
| VALIDATION | 1.35 | 1.35 | 2.70 |
| Combined | 1.44 | 1.44 | 2.89 |

The three tokenizer-audit examples were 221–228 prompt tokens, but they are
contract probes rather than dataset-level performance measurements. The future
three-second median/eight-second P95 family targets remain untested research
targets, not product claims.
