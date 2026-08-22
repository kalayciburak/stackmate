# Phase 5.4 reproducibility

The run stopped before one-shot training. Reproducible evidence consists of:

- official base revision and Phase 5.3 dataset hashes from `baseline.md`;
- BF16 smoke summary `phase-5-4-bf16-smoke.json`;
- BF16 B0 summary `phase-5-4-b0-bf16-validation.json`;
- NF4 B0 summary `phase-5-4-nf4-base-validation.json`;
- exact scripts/configuration retained in the research tree;
- full raw reports retained outside Git under the WSL Phase 5.4 cache.

No adapter selection commit, final receipt, merged model, GGUF, or production
artifact was produced.
