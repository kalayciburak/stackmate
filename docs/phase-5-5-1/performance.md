# Phase 5.5.1 performance

No model inference or training ran. Sequential/batched latency, peak VRAM, tokens
per retained pair and pairs per second are therefore `NOT_MEASURED`.

The raw canonical source has this candidate fan-out:

| Split | Mean cause pairs/family | Mean check pairs/family | Mean total pairs/family |
|---|---:|---:|---:|
| TRAIN | 1.47 | 1.47 | 2.94 |
| VALIDATION | 1.35 | 1.35 | 2.70 |

These counts do not establish product runtime cost because Gate B prevents a
frozen binary dataset. The future three-second median and eight-second P95 family
targets remain untested research targets.
