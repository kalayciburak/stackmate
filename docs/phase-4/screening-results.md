# Phase 4 English small-model screening results

Date: 2026-08-02

Status: **Gate B failed. No candidate advances to Gate C.**

The frozen V3 screen contains 20 English cases: 10 development, 10 holdout,
16 real runtime/integration, and four adversarial. Each candidate received the same
prompt/schema version 2, 2,048-token context, 192-token output bound, 16 CPU threads,
120-second timeout, strict JSON schema, semantic validator, seed 42, and two bounded
generation profiles. Only the official chat envelope differed.

## Environment

| Field | Actual value |
|---|---|
| OS | Windows 11 Pro 10.0.22631, x86_64 |
| CPU | AMD Ryzen 9 7950X, 16 cores / 32 logical processors |
| Physical memory | approximately 63.15 GiB |
| Java | Amazon Corretto 21.0.12 |
| llama.cpp | `b10221`, commit `815a2a5915f22ce6a760c676389c5dfe8535c08f` |
| Quantization | Q4_K_M for every role |
| Corpus SHA-256 | `e8f9fa194f0b53c78a35e2821ed6358195b0d535c86e82e4fc50a9b8f0085a78` |
| Candidate manifest SHA-256 | `a79c5f359b4bcb05f927eb8921f5fc32109db9abc6c3b9b706a47590ad0f68d2` |

The harness starts a fresh process for each case. `eval` is llama.cpp generation
timing and is used as the warm-generation proxy. Process timing includes process
startup, model mapping, prompt processing, and output handling. Peak working set was
sampled every 100 ms from `llama-completion.exe`.

## Authoritative profile results

| Role / profile | Eligible | Valid | Cause | Check | Holdout cause | Holdout check | Useful prose | Eval median / P95 | Peak working set | Classification |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| MICRO / GREEDY | yes | 20/20 | 18/20 | 18/20 | 8/10 | 8/10 | 0/20 | 487.80 / 660.58 ms | 464,605,184 B | `ELIMINATED_QUALITY` |
| COMPACT / RECOMMENDED | yes | 20/20 | 19/20 | 18/20 | 9/10 | 8/10 | 19/20 | 942.60 / 1,283.60 ms | 1,009,168,384 B | `ELIMINATED_QUALITY` |
| MID / GREEDY | yes | 19/20 | 16/20 | 15/20 | 7/10 | 6/10 | 2/20 | 1,573.16 / 1,922.79 ms | 1,865,826,304 B | `ELIMINATED_SAFETY` |
| historical control / GREEDY | **no** | 20/20 | 20/20 | 20/20 | 10/10 | 10/10 | 20/20 | 2,453.68 / 2,717.93 ms | 2,130,599,936 B | `ELIMINATED_COMPATIBILITY` |

Manual usefulness decisions and reasons are in
[screening-manual-review.md](screening-manual-review.md). The sanitized per-case
machine reports remain in ignored `stackmate-benchmark/target/phase4/screening/`
directories. They are not committed because they contain machine-specific paths and
timing.

### Alternative-profile results

| Role / profile | Valid | Cause | Check | Holdout cause | Holdout check | Eval median / P95 | Repair attempts / successes |
|---|---:|---:|---:|---:|---:|---:|---:|
| MICRO / RECOMMENDED | 17/20 | 15/20 | 12/20 | 8/10 | 6/10 | 552.66 / 857.99 ms | 3 / 0 |
| COMPACT / GREEDY | 18/20 | 15/20 | 16/20 | 7/10 | 7/10 | 812.89 / 1,358.36 ms | 2 / 0 |
| MID / RECOMMENDED | 19/20 | 16/20 | 12/20 | 7/10 | 5/10 | 1,577.15 / 1,949.04 ms | 1 / 0 |
| control / RECOMMENDED | 20/20 | 18/20 | 18/20 | 8/10 | 8/10 | 2,420.93 / 2,982.94 ms | 0 / 0 |

## Candidate identities

| Role | Official revision | Total parameters | GGUF revision | Bytes | Verified SHA-256 |
|---|---|---:|---|---:|---|
| MICRO SmolLM2-360M-Instruct | `a10cc1512eabd3dde888204e902eca88bddb4951` | 361,821,120 | `7be6f65f1db715fe5dc5a4634c0d459b4eed42ec` | 270,590,880 | `2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2` |
| COMPACT Qwen3-0.6B | `c1899de289a04d12100db370d81485cdf75e47ca` | 751,632,384 | `60b85c0e3d8fe0f6474f406922a26d12aca4550d` | 484,220,320 | `9acfc1e001311f34b4252001b626f2e466d592a42065f66571bff3790d4e1b14` |
| MID Qwen2.5-1.5B-Instruct | `989aa7980e4cf806f80c7fef2b1adb7bc71aa306` | 1,543,714,304 | `91cad51170dc346986eccefdc2dd33a9da36ead9` | 1,117,320,736 | `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e` |
| control Qwen3.5-2B | `15852e8c16360a2fea060d615a32b45270f8a8fc` | 2,274,069,824 | `7d26695454df6de5fbcce2e58681e62dae06ce43` | 1,396,198,496 | `57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8` |

All upstream model cards declare Apache-2.0. MICRO and COMPACT GGUFs are
third-party feasibility artifacts. MID comes from the official Qwen GGUF repository.
The control is the exact third-party Phase 3 artifact and is ineligible because the
official multimodal total exceeds 2,000,000,000.

## Safety observations

The authoritative four profiles cover 80 case results and 82 model invocations due
to one failed MID repair and one successful COMPACT repair.

- accepted unknown evidence/cause/check IDs: 0;
- accepted fabricated references: 0;
- secret leakage: 0;
- prompt-injection success: 0;
- hidden thinking in trusted output: 0;
- timeouts/crashes: 0/0;
- deterministic fallback availability for rejected outputs: 100%;
- model processes remaining after all runs: 0.

The COMPACT model initially emitted a short Chinese fragment in the insufficient-
evidence case. The old English heuristic did not reject it. Phase 4 hardened the
validator to reject letters outside the Latin script; the result was quarantined as
`OUTPUT_NOT_ENGLISH`, one bounded repair returned English JSON, and no mismatched
text became trusted output. This is recorded as one generated language mismatch and
zero accepted mismatches.

The GREEDY COMPACT and both MID profiles generated one fabricated technical/source
identifier each. All were rejected, so accepted fabricated references remained zero.
Containment succeeded; model quality did not.

## Latency interpretation

The first measured per-process load times were 198.82 ms (MICRO), 298.63 ms
(COMPACT), 583.70 ms (MID), and 675.84 ms (control). They are **not true cold-load
measurements** because downloading, checksum verification, earlier smoke runs, or
Phase 3 execution may have populated the OS file cache. No cold-cache claim is made.

All eligible candidates met their tier's file, working-set, and generation-latency
targets on this machine. Resource success does not compensate for failed holdout,
validity, or usefulness gates.

## Gate decision

- MICRO fails useful prose (0/20) and holdout cause/check (80%/80%). Its literal
  `concise string` summary/explanation proves schema compliance is not usefulness.
- COMPACT fails holdout check selection (80%); the required minimum is 85% and no
  rounding is allowed.
- MID fails the mandatory 20/20 valid-output gate and both selection gates.
- The historical control passes the empirical English screen but is ineligible at
  2,274,069,824 total parameters.

No eligible candidate passes every gate. Gate B is **FAIL**. Gate C full Benchmark
V2 and persistent-worker work are not authorized, and Gate D production adapter is
not implemented. Deterministic diagnostics remain `READY`; local inference remains
`NOT_VIABLE`.
