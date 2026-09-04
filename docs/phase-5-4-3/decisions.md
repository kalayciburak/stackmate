# Phase 5.4.3 decisions

- Keep checkpoint 18, its adapter bytes, the official base revision, and the
  authoritative ordinal T1 contract unchanged.
- Build a dedicated corpus-v6 path rather than extending or relabeling v2-v5.
- Separate generation inputs from scoring-only labels by resource root and
  process boundary.
- Require 24 executed real runtime/integration recipes and exactly six
  controlled synthetic/adversarial families before freeze.
- Reject lexical novelty and compare each proposal against every exposed family
  using normalized semantic signatures plus manual review.
- Complete all D0/B0/A1 generation before loading labels or computing scores.
- Use aggregate-balanced SHA-256 candidate ordering independent of expected
  IDs, followed by a one-position permutation; require every supported
  cause/check to move.
- Count a family correct only when both ORIGINAL and PERMUTED predictions are
  correct.
- Reuse only specific existing deterministic-analyzer selections in D0;
  generic fallbacks and benchmark-only candidates require abstention.
- Stop before merge/GGUF if any fresh-final safety, quality, or practical-value
  gate fails.
- Gate E failed; preserve checkpoint-18 as technically valid but experimental,
  perform no retraining, and classify fine-tuning value as not justified.
- Preserve the frozen scorer failure and use only the documented scorer wrapper
  that maps invalid-output nullable accuracy to incorrect; do not regenerate.
- Create local commits and tags where required; perform no remote push.
