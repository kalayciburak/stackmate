# Phase 5.3 evaluation-path parity

Date: 2026-08-22

## Controlled 40-case development results

All rows use the frozen parity corpus, P0 or P3, greedy generation, seed 42,
maximum 192 output tokens, reasoning off, the same production request payload, and
the same authoritative Java validator. `Valid` means validator-valid structured
output.

| Path | Prompt | Decoding | Valid | Cause | Check | Unknown IDs | Restraint |
|---|---|---|---:|---:|---:|---:|---:|
| Third-party Q4_K_M, llama.cpp | P0 | schema grammar | 32/40 | 35/40 | 35/40 | 0 | 0/8 |
| Third-party Q4_K_M, llama.cpp | P3 | schema grammar | 40/40 | 38/40 | 38/40 | 0 | 8/8 |
| Third-party Q4_K_M, llama.cpp | P0 | unconstrained | 0/40 | 0/40 | 0/40 | 0 | 0/8 |
| Third-party Q4_K_M, llama.cpp | P3 | unconstrained | 29/40 | 27/40 | 34/40 | 11 | 8/8 |
| Official safetensors, Transformers BF16 | P0 | unconstrained | 24/40 | 23/40 | 23/40 | 0 | 0/8 |
| Official safetensors, Transformers BF16 | P3 | unconstrained | 28/40 | 25/40 | 30/40 | 12 | 8/8 |
| Official safetensors, Transformers NF4 | P0 | unconstrained | 0/40 | 0/40 | 0/40 | 0 | 0/8 |
| Official safetensors, Transformers NF4 | P3 | unconstrained | 22/40 | 18/40 | 18/40 | 7 | 8/8 |
| Official NF4 + F1 adapter | P3 | unconstrained | 31/40 | 26/40 | 26/40 | 9 | 8/8 |
| Official-converted Q4_K_M, llama.cpp | P0 | schema grammar | 32/40 | 35/40 | 35/40 | 0 | 0/8 |
| Official-converted Q4_K_M, llama.cpp | P3 | schema grammar | 34/40 | 35/40 | 34/40 | 0 | 3/8 |
| Official-converted BF16, llama.cpp | P0 | schema grammar | 32/40 | 35/40 | 35/40 | 0 | 0/8 |
| Official-converted BF16, llama.cpp | P3 | schema grammar | 40/40 | 36/40 | 36/40 | 0 | 8/8 |

## Generation-parameter mapping

| Parameter | llama.cpp | Transformers | Equivalence |
|---|---|---|---|
| Temperature / sampling | `temperature=0` | `do_sample=false` | Equivalent greedy selection |
| Top-k | `1` | not applied in greedy mode | Approximately equivalent |
| Top-p | `1.0` | not applied in greedy mode | Equivalent disabled nucleus filter |
| Min-p | default `0.05` | unavailable in greedy call | Not operational after top-k 1 / temperature 0 |
| Typical-p | default `1.0` | unavailable in greedy call | Equivalent disabled |
| Repetition penalty | `1.0` | default `1.0` | Equivalent disabled |
| Presence/frequency | `0/0` | unavailable/default none | Equivalent disabled |
| Seed | `42` | `42` | Recorded; irrelevant to greedy sampling |
| Context | `2,048` | prompts measured <=1,296; model context larger | Comparable for this corpus |
| Maximum output | `192` | `192` | Equivalent bound |
| Thinking | `--reasoning off` plus prefix | `enable_thinking=false` | Token-equivalent after transport fix |
| Chat template | manual Qwen envelope | official tokenizer template | 80/80 token-equivalent after fix |
| Grammar | strict request-scoped JSON schema | none | Not equivalent; major behavior change |
| Lifecycle | process per case | one model instance per report | Not equivalent; affects latency/state, not prompt tokens |
| Repair | none in primary parity | none | Equivalent |

## Evidence-backed discrepancy explanation

The historical headline compared different worlds:

1. The strong llama.cpp baseline used request-scoped JSON grammar that constrained
   field types and candidate/evidence IDs. Removing only that grammar reduced P3
   from 40/40 to 29/40 validator-valid and generated 11 unknown IDs. P0 remained
   valid JSON but mapped to the wrong schema in all 40 cases.
2. Phase 5.2 loaded the official base in NF4. On the same development cases, BF16
   P0 was 24/40 validator-valid while NF4 P0 was 0/40; NF4 P3 also fell from
   28/40 to 22/40. The locked B0 collapse is reproducible as an NF4 path effect.
3. The locked 70-case distribution is materially harder than synthetic validation
   and differs from the 40-case development corpus. F1 scored 26/40 cause/check on
   parity and 44/70–45/70 locked, despite 200/200 synthetic validation.
4. Official-converted Q4_K_M differs from the third-party Q4_K_M under identical
   runtime/grammar controls, especially restraint: P3 was 3/8 versus 8/8. Conversion
   provenance or weight content therefore materially affects behavior.
5. Quantizing the official BF16 GGUF to Q4_K_M reduced P3 from 40/40
   validator-valid, 36/40 cause/check, and 8/8 restraint to 34/40,
   35/40–34/40, and 3/8. Official quantization drift is therefore measurable.
6. Prompt-file newline transport changes raw output but not the main aggregate P3
   result. It is bounded and not the root cause.

Evaluation parity is explained as a combination of non-equivalent constrained
decoding, damaging NF4 base behavior, different evaluation distributions, and a
real third-party-versus-official conversion difference. It is not evidence that
one runtime alone is intrinsically superior.
