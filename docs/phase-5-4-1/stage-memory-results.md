# Stage memory results

The reported CUDA device total was 17,170,956,288 bytes (16,376 MiB). The
required practical headroom is at least 1 GiB.

| Stage | Allocated peak | Reserved peak | Minimum `nvidia-smi` free | Free at report snapshot | Retries | OOMs |
|---|---:|---:|---:|---:|---:|---:|
| M0 baseline | 4,427,087,872 | 4,431,282,176 | 10,432 MiB | 11,350,835,200 | 0 | 0 |
| M1 baseline | 4,460,210,688 | 4,466,933,760 | 10,449 MiB | 11,315,183,616 | 0 | 0 |
| M2 baseline | 7,736,681,472 | 17,402,167,296 | **87 MiB** | **0** | 0 | 0 |
| M3 baseline | 4,589,963,776 | 4,645,191,680 | 9,943 MiB | 11,118,051,328 | 0 | 0 |
| M4 baseline | 4,611,882,496 | 4,703,911,936 | 10,166 MiB | 11,059,331,072 | 0 | 0 |
| M0 expandable | 4,426,507,264 | 4,450,156,544 | 10,650 MiB | 11,331,960,832 | 0 | 0 |
| M1 expandable | 4,460,210,688 | 4,485,808,128 | 10,691 MiB | 11,296,309,248 | 0 | 0 |
| M2 expandable | 7,735,497,728 | 8,833,204,224 | **6,234 MiB** | **6,921,650,176** | 0 | 0 |
| M3 expandable | 4,586,905,088 | 4,609,540,096 | 10,509 MiB | 11,153,702,912 | 0 | 0 |
| M4 expandable | 4,609,946,112 | 4,659,871,744 | 10,461 MiB | 11,103,371,264 | 0 | 0 |

Baseline M2 failed the headroom requirement even without an OOM. The single
allocator experiment reduced reserved peak by 8,568,963,072 bytes and passed
the headroom requirement. M2 expandable loss was finite for all 10 steps and
the adapter save completed.
