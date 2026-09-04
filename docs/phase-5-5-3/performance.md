# Phase 5.5.3 performance

No new latency benchmark was run. Gate B used no model or GPU, and the
model-eligible, O0-O4, adapter and H1 paths do not exist; therefore Phase 5.5.3
has no single-pair, batched or hybrid family latency, token, throughput or VRAM
measurement.

The only available model timing is the historical Phase 5.5.2 base evaluation:

```text
File-order pass          21.46 s, 28.52 pairs/s
Hash-order pass          20.02 s, 30.57 pairs/s
Peak allocated VRAM      4,897,159,680 bytes
Peak reserved VRAM       4,940,890,112 bytes
Estimated family median  0.240 s
Estimated family P95     0.412 s
```

These historical batch-8 values are not a Phase 5.5.3 latency result and do not
qualify a hybrid product path.

