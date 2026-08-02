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
| P1 | 20/20 | 18/20 | 18/20 | 4/4 | 4/4 | 0 | 1 | 19/20 | 3/20 | 17/20 | 1/20 | 4,575 ms | 5,536 ms |
| P2 | 20/20 | 18/20 | 18/20 | 4/4 | 4/4 | 0 | 0 | 20/20 | 4/20 | 16/20 | 0/20 | 4,794 ms | 5,376 ms |
| P3 | 20/20 | 18/20 | 18/20 | 4/4 | 4/4 | 0 | 1 | 19/20 | 3/20 | 17/20 | 1/20 | 4,862 ms | 5,043 ms |

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
terminology for that case. A second reverse-order technical pass, performed after
the prompt had been locked and Gate B had run, found a selection-time review error:
P1 and P3 described `CONC-REJECTED-004` as a `high rejection rate`. The supplied
evidence established rejection, not a measured rate. P2 remained evidence-only.
The automatic detector reported zero for every prompt, so these manual findings
remain separate and demonstrate the known validator gap.

The correction does not use locked-final answers and does not alter model output,
ground truth, or prompt bytes. It changes the authoritative selection-validation
manual counts shown above. The original selection-time pass is retained as an
auditable reviewer error rather than silently rewritten.

P2's seven confusing development outputs were two truncated-but-schema-valid
explanations plus five wrong generic database selections. P3's four confusing
outputs selected a supplied generic database candidate instead of the expected
more specific supplied candidate. These are selection-quality failures, not
unsupported external claims.

## Selection implication

P0 is excluded by an accepted unsupported claim, 1/8 strict abstention, and three
generic-fallback misuses. P1 is excluded by an accepted unsupported claim, 7/8
strict abstention, and one invalid output. P2 has zero manually accepted
unsupported claims and complete restraint, but only 53/60 exact cause/check
selections (88.33%). P3 has complete restraint, zero fallback misuse, and 60/60
validity, but its corrected manual count contains one accepted unsupported claim.

P3 had already been selected and checksum-locked under the erroneous first pass.
The lock was honored and was not switched after Gate B. In the final audit, no
variant satisfies every Gate A requirement.

## Clause-level grounding sub-experiment

After the primary prompt comparison, a separate schema ablation replaced the two
free text fields with `{text, evidenceIds}` objects on the same 20-case
selection-validation set. The P3 decision rules and all inference controls stayed
fixed; only the tightly related prompt/schema contract changed.

| Metric | Field-level P3 | Clause-level experiment |
|---|---:|---:|
| Prompt characters / estimated tokens | 3,259 / 815 | 3,352 / 838 |
| Valid output | 20/20 | 20/20 |
| Cause/check accuracy | 18/20 / 18/20 | 18/20 / 18/20 |
| Clauses with required supplied evidence IDs | not required | 20/20 |
| Accepted unsupported claim, manual | 1 | 1 |
| Average semantic text characters | 126.3 | 118.2 |
| Median / P95 process latency | 4,862 / 5,043 ms | 5,101 / 5,199 ms |
| Median generation latency | 1,635.04 ms | 1,920.95 ms |

The report SHA-256 is
`97a5b85774bf7fed7e55c244417e922def8de0130ae939a7fecf398bd5da88f5`;
the experiment prompt SHA-256 is
`168bfb3a6f9eaa5843b1a47d4ae0fe30f2dbd0f237157e54258ac3831ed5f2c1`.

The evidence objects improve auditability without improving selection or semantic
entailment: the model cited `java.class.0` while still adding `high rejection
rate`. The schema is not adopted and no ADR is created.
