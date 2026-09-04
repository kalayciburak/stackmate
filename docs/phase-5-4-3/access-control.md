# Phase 5.4.3 access control

The generation runner accepts a frozen manifest and generation-input resource;
it has no scoring-label argument and does not import the T1 scorer. It rejects
recursive label-bearing fields and validates request, message, prompt, schema,
base, adapter, parser, scorer, and generation-config bindings.

Before any output it also requires:

- an annotated `phase-5-4-3-fresh-final-frozen` tag;
- HEAD equal to that tag's commit;
- a clean worktree;
- the tagged manifest blob to match the supplied SHA-256;
- D0, then B0, then A1 receipt order;
- an unused per-system output directory.

Once a system directory exists, the runner refuses a second attempt. Raw
predictions are flushed and fsynced per row. Generation writes no metrics or
scores. The separate scorer verifies all 60 raw-output hashes and all three
execution receipts before opening the sealed label file. Scoring can be
repeated without model generation.

The normal Java classpath contains generation inputs and the freeze manifest,
but not `research/model-alignment/sealed/phase-5-4-3-labels`.
