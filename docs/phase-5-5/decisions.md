# Phase 5.5 decisions

- Preserve the Phase 5.4.3 listwise verdict and all raw reports unchanged.
- Use only the official Qwen3.5-2B revision as a possible pairwise training
  base; do not initialize from checkpoint-18.
- The model may classify one candidate only; it may not receive sibling
  candidates, positions, persistent IDs, expected labels, or diagnostic prose
  targets.
- Freeze one-token labels, serialization, family splits, seed, LoRA settings,
  and logit scoring before any base-model execution.
- Do not infer `CONTRADICTED` merely because a listwise candidate was not
  selected. Explicit candidate-targeted counterevidence is required.
- Do not assume a listwise-selected set is complete pairwise entailment truth;
  generic evidence-backed candidates require candidate-level review.
- If a defensible three-class, family-separated dataset cannot be derived from
  the permitted sources without synthetic label invention, fail Gate B and do
  not run the base model or training.
- Exposed diagnostic corpora remain unavailable until contract, training,
  checkpoint selection, validation thresholds, and aggregation are frozen.
- Create local commits where the phase requires them; perform no remote push.
