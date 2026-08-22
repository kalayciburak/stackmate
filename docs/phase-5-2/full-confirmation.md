# Phase 5.2 full confirmation

Date: 2026-08-02

## Resumed continuation decision

Full confirmation remained `NOT_RUN`. The selected F1 adapter failed the locked
valid-structured and cause/check accuracy gates: A1 was 68/70 structured, 44/70
cause, and 45/70 check. The protocol therefore stopped before the planned 100
Phase 5 qualification cases plus 30 challenge cases. No locked result was used to
select another adapter, prompt, or run.

The full confirmation is `NOT_RUN`. It depends on a selected adapter passing the
locked V2, blind, and challenge stages. No such adapter exists.

Phase 5 P0 and Phase 5.1 P3 historical results remain unchanged. There are no
fine-tuned-adapter or concise-prompt results to compare, and zero values are not
substituted for missing executions.

| Metric | Phase 5 P0 | Phase 5.1 P3 | Adapter + P3 | Adapter + concise |
|---|---:|---:|---:|---:|
| Valid output | 100/100 | 100/100 observed across disjoint runs | NOT_RUN | NOT_RUN |
| Cause accuracy | 94/100 | 88/100 | NOT_RUN | NOT_RUN |
| Check accuracy | 94/100 | 88/100 | NOT_RUN | NOT_RUN |
| Strict abstention | 1/11 | 11/11 | NOT_RUN | NOT_RUN |
| Generic fallback misuse | 4 | 0 | NOT_RUN | NOT_RUN |
| Accepted unsupported claims | 3 | 1 | NOT_RUN | NOT_RUN |
| Useful explanation | 91/100 | 90/100 | NOT_RUN | NOT_RUN |
| Adds meaningful clarity | 51/100 | 45/100 | NOT_RUN | NOT_RUN |
| Confusing output | 4/100 | 9/100 | NOT_RUN | NOT_RUN |
