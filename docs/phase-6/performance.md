# Phase 6 performance evidence

These are local measurements, not service-level guarantees. HTTP measurements
used 20 warmups followed by 200 sequential measured requests per mode. Startup
used a fresh JVM for each sample with 2 excluded warmups and 10 measured starts
per mode.

## Measurement environment

```text
CPU                 AMD Ryzen 9 7950X 16-Core Processor
Logical processors  32
RAM                 67,812,388,864 bytes (63.16 GiB reported)
OS                  Microsoft Windows 11 Pro 10.0.26200, build 26200
JVM                 Amazon Corretto 21.0.12.1+9-LTS
Maven               3.9.16
Spring Boot         4.1.0
Spring Framework    7.0.8
```

## Final RC deterministic JMH

| Operation | Mean | p50 | p95 | p99 | Unit |
|---|---:|---:|---:|---:|---|
| Capture and diagnosis | 322.10 | 310.27 | 383.54 | 515.29 | us/op |
| Fingerprint | 16.35 | 15.49 | 21.70 | 28.19 | us/op |
| Sanitizer | 105.91 | 101.76 | 137.98 | 157.18 | us/op |
| Deduplication churn | 0.07 | not recorded | 0.10 | not recorded | us/op |
| Deduplication hit | 0.05 | not recorded | 0.10 | not recorded | us/op |
| Deduplication miss | 0.04 | not recorded | 0.10 | not recorded | us/op |

The raw JMH report is intentionally outside Git. Its SHA-256 is
`ba13b7865e67ed80cfe4896018c50ec7edb2f471045eb0b91e754e659c9bae6a`.
These final RC figures supersede the earlier snapshot values; the earlier raw
evidence should be retained rather than rewritten.

## Bounded deduplication retained-heap probe

The fresh-JVM probe filled the deduplication cache to its supported maximum of
10,000 entries using 64-character fingerprints. It ran 7 forks with Serial GC
and a fixed 128 MiB heap.

| Statistic | Approximate retained bytes | Approximate MiB |
|---|---:|---:|
| Minimum | 510,912 | 0.49 |
| Median | 510,936 | 0.49 |
| p95 / maximum | 1,226,608 | 1.17 |

The probe passed. Its heap-delta method is approximate and includes JVM/GC
noise; these values are not an exact object-layout measurement. The raw report
is outside Git with SHA-256
`cf9eab70a0cc53ed531bbc5e60e0a8458fc2d8074d7edd2170915632fe9ceda1`.

## HTTP snapshot

| Mode | Success median/p95 (ms) | Success rps | Failure median/p95 (ms) | Failure rps |
|---|---:|---:|---:|---:|
| Without StackMate | 0.42 / 0.80 | 1390.19 | 1.39 / 2.24 | 579.06 |
| Starter disabled | 0.42 / 0.78 | 1667.00 | 1.48 / 2.48 | 558.57 |
| Deterministic enabled | 0.39 / 0.68 | 1900.12 | 5.44 / 7.77 | 169.00 |

Status codes and body hashes were identical across all three modes for both
success and failure endpoints. Throughput is included as observed output; the
sequential local harness is not a capacity benchmark.

The raw HTTP report is intentionally outside Git. Its SHA-256 is
`6499e244438be2fb95e2d2afbdcb37ba2a66f8ad5fbd414d2193b1dc214e13b8`.

## Fresh-JVM startup snapshot

| Mode | Median (ms) | p95 (ms) |
|---|---:|---:|
| Without StackMate | 1579 | 1618 |
| Starter disabled | 1581 | 1628 |
| Deterministic enabled | 1633 | 1685 |

The enabled median delta versus the without-StackMate fixture is +54 ms, about
3.4%.

The raw startup report is intentionally outside Git. Its SHA-256 is
`9e67a7dd008e135d972651f7b4d7f1559858573bf2583d6019b8ea4d690823da`.
