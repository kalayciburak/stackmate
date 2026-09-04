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
- Stop before merge/GGUF if any fresh-final safety, quality, or practical-value
  gate fails.
- Create local commits and tags where required; perform no remote push.
