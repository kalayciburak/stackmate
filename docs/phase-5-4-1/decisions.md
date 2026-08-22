# Phase 5.4.1 decisions

- Keep the official Qwen3.5-2B checkpoint and immutable revision unchanged.
- Keep the Phase 5.3 real-world corpus and all sealed-final descriptors unchanged.
- Measure BF16 stages M0 through M4 in separate processes.
- Do not return to NF4 training; Phase 5.4 already rejected it on restraint and
  fallback behavior.
- Use no sequence cap: the actual tokenizer audit shows no truncation, and the
  trainer uses dynamic padding rather than fixed padding.
- Use exactly one allocator mitigation, `expandable_segments:True`, because
  baseline M2 exhausted device-free headroom while showing a large reserved
  versus active allocation gap.
- Run at most one real training run, followed by validation-only checkpoint
  selection. No additional hyperparameter run is authorized.
- Keep final access fail-closed until a validation selection record and the
  selection commit exist.
