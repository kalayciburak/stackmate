# Phase 5.4 training precision decision

Date: 2026-08-22

| Path | Joint | Strict restraint | Safety issue | Decision |
|---|---:|---:|---|---|
| BF16 base | 33/40 | 22/22 | smoke reserved VRAM leaves <1 GiB headroom | reject for resource safety |
| NF4 base | 29/40 | 18/22 | 1 unknown index, 3 fallback misuses | reject for behavior safety |

The BF16 LoRA smoke test itself passed finite loss, adapter save/reload and exact T1
probe requirements, but its reserved VRAM peak was unsafe. The NF4 fallback was
measured before any training and materially damaged restraint. No precision mode is
both safe and feasible under the frozen Phase 5.4 policy.

```text
Gate B: FAIL
Real training: NOT_RUN
Locked final: NOT_RUN
Merge/GGUF: NOT_RUN
```
