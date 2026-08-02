# Phase 5.1 prompt-ablation results

Date: 2026-08-02

## Controlled inputs

All variants used the pinned Qwen3.5-2B Q4_K_M GGUF, official llama.cpp
`b10223`, context 2,048, 192 output tokens, greedy decoding, seed 42, reasoning
disabled, the Phase 5 JSON schema and validator, a 120-second timeout, and one
process per case. Only the versioned system prompt changed.

The development report SHA-256 is
`00267a403e7241504eb67894d174e1d87f12dde58d9c5a75be1488fedea159e6`.
The selection-validation report SHA-256 is
`6aecc87e56038abc2ba5ac383f3748fc63125f54246c6fe67b6cba4c8c54da16`.
Generated reports remain under `stackmate-benchmark/target/phase-5-1-ablation/`
and are intentionally ignored.

## Prompt inventory

| Prompt | Version | Characters | Estimated tokens | SHA-256 |
|---|---|---:|---:|---|
| P0 | `phase5-schema-v2` | 1,270 | 318 | `3362afedc99b7c930a8423a33d011bb8b8a026d7072befef6041f6629b36fa03` |
| P1 | `phase5.1-p1-v1` | 1,948 | 487 | `18bb858befb6e9ce78286d6e751b197d5da6ee737c5671a78fb47f4b8d6a6f24` |
| P2 | `phase5.1-p2-v1` | 2,243 | 561 | `9f40df4491fcb5a009b05d94c505bb4f1105e34f0fd48c7231bbae78fdd7e213` |
| P3 | `phase5.1-p3-v1` | 3,259 | 815 | `d5117da41bd8af64982035fb75dbac91968249ea48fd02f120c532755074bf7f` |

The estimate is the frozen `ceil(characters / 4)` approximation, not a
model-tokenizer count.

## Prompt-development results

| Prompt | Valid | Cause | Check | Strict abstention | Explicit limitation | Fallback misuse | Accepted unsupported, manual | Useful | Adds clarity | Repeats | Confuses | Median | P95 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| P0 | 40/40 | 39/40 | 39/40 | 1/4 | 1/4 | 1 | 1 | 36/40 | 24/40 | 16/40 | 1/40 | 5,250 ms | 6,007 ms |
| P1 | 39/40 | 36/40 | 36/40 | 3/4 | 3/4 | 0 | 0 | 34/40 | 23/40 | 17/40 | 1/40 | 4,997 ms | 5,920 ms |
| P2 | 40/40 | 35/40 | 35/40 | 4/4 | 4/4 | 0 | 0 | 33/40 | 19/40 | 21/40 | 7/40 | 5,399 ms | 6,231 ms |
| P3 | 40/40 | 36/40 | 36/40 | 4/4 | 4/4 | 0 | 0 | 36/40 | 21/40 | 19/40 | 4/40 | 5,340 ms | 6,236 ms |

P1 made one bounded repair attempt for `DB-H2-CONVERSION-007`; the repaired
response remained invalid and deterministic fallback succeeded. P0, P2, and P3
made no repair attempt. There were no timeouts, process failures, unknown IDs,
fabricated references, secret leaks, injection successes, or hidden-thinking
fragments.

## Selection-validation results

| Prompt | Valid | Cause | Check | Strict abstention | Explicit limitation | Fallback misuse | Accepted unsupported, manual | Useful | Adds clarity | Repeats | Confuses | Median | P95 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| P0 | 20/20 | 17/20 | 17/20 | 0/4 | 0/4 | 2 | 0 | 20/20 | 4/20 | 16/20 | 0/20 | 4,742 ms | 5,545 ms |
| P1 | 20/20 | 18/20 | 18/20 | 4/4 | 4/4 | 0 | 0 | 20/20 | 4/20 | 16/20 | 0/20 | 4,575 ms | 5,536 ms |
| P2 | 20/20 | 18/20 | 18/20 | 4/4 | 4/4 | 0 | 0 | 20/20 | 4/20 | 16/20 | 0/20 | 4,794 ms | 5,376 ms |
| P3 | 20/20 | 18/20 | 18/20 | 4/4 | 4/4 | 0 | 0 | 20/20 | 4/20 | 16/20 | 0/20 | 4,862 ms | 5,043 ms |

All four variants remained structurally valid on selection validation. P1-P3
abstained on `SECURITY-SIGNATURE-003` and `SECURITY-CERT-004`, where the frozen
ground truth selected supplied generic fallback IDs. This is safe over-restraint,
but it is still scored as an exact cause/check miss and was not re-labeled after
observing output.

## Manual semantic pass

One reviewer read all 240 development and selection-validation outputs in corpus
order. This is the prompt-selection pass, not the final required two-pass review.
The rubric is unchanged from Phase 5: useful means accurate or honestly limited
and not materially misleading; clarity requires information beyond repeating the
deterministic fields; confusion means weakening or redirecting the diagnosis.

P0 reproduced the accepted unsupported alternative in `DB-POOL-TIMEOUT-017`:
`pool exhaustion or driver issues`. P1, P2, and P3 stayed within supplied
terminology for that case. No manual unsupported alternative was found in the
other development or selection-validation outputs. The automatic detector
reported zero for every prompt, so the P0 manual finding remains separate and
demonstrates the known validator gap.

P2's seven confusing development outputs were two truncated-but-schema-valid
explanations plus five wrong generic database selections. P3's four confusing
outputs selected a supplied generic database candidate instead of the expected
more specific supplied candidate. These are selection-quality failures, not
unsupported external claims.

## Selection implication

P0 is excluded by an accepted unsupported claim, 1/8 strict abstention, and three
generic-fallback misuses. P1 is excluded by 7/8 strict abstention and one invalid
output. P2 has complete restraint but only 53/60 exact cause/check selections
(88.33%). P3 has complete restraint, zero fallback misuse, 60/60 validity, no
manual unsupported claim, and 54/60 exact cause/check selections (90%). Under the
predeclared priority order, P3 is the selected candidate for locked evaluation.
