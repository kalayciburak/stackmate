# Phase 5.5.2 performance

Official BF16 base inference used batch size 8 and logits_to_keep=1.

| Pass | Pairs | Duration | Throughput | Batch median | Batch P95 |
|---|---:|---:|---:|---:|---:|
| File order | 612 | 21.46 s | 28.52 pairs/s | 0.252 s | 0.334 s |
| Hash order | 612 | 20.02 s | 30.57 pairs/s | 0.250 s | 0.312 s |

~~~text
Peak allocated VRAM  4,897,159,680 bytes
Peak reserved VRAM   4,940,890,112 bytes
Maximum input        486 tokens
Frozen maximum       1024 tokens
~~~

Allocated batch latency produces an estimated normal family median/P95 of
0.240/0.412 seconds across 85 development families. This is not a separately
measured sequential worker latency and excludes production process overhead.
No persistent worker or unbatched production path was implemented.

Runtime cost was practical on this machine, but quality and order-invariance
gates failed first.
