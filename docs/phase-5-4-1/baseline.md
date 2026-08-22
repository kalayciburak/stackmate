# Phase 5.4.1 baseline

Date: 2026-08-23

This is a continuation of the Phase 5.4 one-shot stop. It does not reopen or
rewrite the historical Phase 5.4 reports.

| Item | Value |
|---|---|
| Branch | `phase-5-4-1-memory-attribution-and-resume` |
| Starting commit | `d5e4af602473dbd16382c3c90fd8d046b04a88ba` |
| Starting tag | `phase-5-4-complete` |
| Official base | `Qwen/Qwen3.5-2B@15852e8c16360a2fea060d615a32b45270f8a8fc` |
| Dataset | Phase 5.3 `real-world-v1`, unchanged |
| Training rows | 140 T1 development rows |
| Validation rows | 40 T1 development rows |
| Sealed final | Not loaded |
| Previous adapter | No Phase 5.4 adapter was selected |

The prior stop proved BF16 smoke correctness but rejected the stage on a
reserved-memory headroom policy. Phase 5.4.1 adds stage-scoped attribution and
one permitted allocator experiment; it does not change the model, corpus,
prompts, generation settings, or locked cases.
