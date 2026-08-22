# Phase 5.3 F1 real-world diagnostic

Date: 2026-08-22

## Boundary

This was one diagnostic inference pass, not training. It loaded the exact official
Qwen3.5-2B revision in NF4 and the already selected Phase 5.2 F1 adapter. It read
only the frozen 40-row Phase 5.3 VALIDATION split using contract T1. TRAIN and the
30 LOCKED_FINAL families were not loaded.

| Control | Value |
|---|---|
| Base revision | `15852e8c16360a2fea060d615a32b45270f8a8fc` |
| F1 adapter SHA-256 | `6642b7e3bad7eb64c6199d56db60935aabda54b5a2c26f33af4e79bc8d5ab6ca` |
| Validation SHA-256 | `bc8e2b5573c57f6927f0e8547968e29fa3bf0f24b221d7bb402b13ba04680789` |
| Contract | T1 selection-only ordinal |
| Quantization / compute | NF4 double quantization / BF16 |
| Generation | greedy, 128-token maximum, thinking disabled |
| Local load | `local_files_only=true`, `trust_remote_code=false` |
| Network / telemetry / uploads | disabled / disabled / none |

## Results

| Metric | Result |
|---|---:|
| Valid JSON | 40/40 |
| Valid exact T1 structure | 18/40 |
| Cause accuracy | 12/40 |
| Check accuracy | 12/40 |
| Joint cause/check accuracy | 11/40 |
| Joint accuracy, DIAGNOSE only | 11/18 |
| Strict restraint | 0/22 |
| Original-order joint accuracy | 8/20 |
| Permuted-order joint accuracy | 3/20 |
| Family order invariance | 3/20 |
| Contract-field mismatch | 22 |
| Missing required fields | 22 |
| Outputs with extra fields | 2 |
| Outputs with prose fields | 2 |
| Unknown indexes | 0 |
| Hidden thinking | 0 |
| Generic fallback misuse | 0 |

Most restraint outputs expressed the correct empty-selection intent but omitted the
required `selectedCheckIndexes` field, so they remained invalid and strict restraint
was 0/22. One output repeated a JSON key and was rejected without repair. Valid
T1 outputs contained no model prose. The output hashes matched in a second complete
run, confirming deterministic generation under the recorded controls.

## Performance

- Inference-only duration: 45.387 seconds for 40 rows.
- Mean generation latency: 1.131341 seconds.
- Peak allocated GPU VRAM: 2,101,252,096 bytes.
- Peak reserved GPU VRAM: 2,164,260,864 bytes.
- Maximum input/output tokens: 1,015 / 25.

## Interpretation

F1 learned the old Phase 5.2 prose/schema task and does not reliably transfer to
the new T1 contract or reordered real-world validation. The result is useful as a
distribution/contract diagnostic; it is not a fair rejection of training on the
new corpus because F1 never saw that corpus or target format. It does rule out
reusing F1 unchanged as the future candidate.

Machine report SHA-256:
`cf3df13b51d90526479e8bb4c378fe65ed4cb6155bfb719db5e128432c2ac1c5`.
Runner SHA-256:
`da8ceb762cd0f942f88fc9cfb6d9dc5affdfc1947be6d51dd7d13be01675e3d4`.
