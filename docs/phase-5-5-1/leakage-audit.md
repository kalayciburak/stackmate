# Phase 5.5.1 leakage audit

TRAIN and VALIDATION remain family-separated: family-ID overlap is zero. The
audit hashes model-facing semantics without persistent IDs and removes all exact
model inputs that carry conflicting proposed labels.

| Retained cross-split check | Overlap |
|---|---:|
| Exact model-input pair | 4 |
| Normalized semantic pair | 4 |
| Candidate text | 9 |
| Candidate-linked evidence | 3 |
| Throwable topology | 3 |
| Category | 5 |
| Exact-pair label conflicts | 0 |

Text repetition alone is not family leakage, but this source has a stronger
shortcut: across all 136 conservatively retained proposals, 29 unique candidate
texts remain and no text appears with both labels. Candidate text therefore
perfectly determines the label without reading evidence.

All 67 retained `NOT_SUPPORTED` proposals use only three repeated text roles:

```text
unknown-cause text             30
inspect-root-cause check       30
JSON-parse check                7
```

This cannot establish that a model learned evidence dependence. Retaining the
noisy counterexamples would reintroduce directly positive unselected evidence or
identical inputs with conflicting labels. Creating counterfactual evidence or
moving candidates between families would change frozen semantics.

Natural candidate and candidate-linked evidence fields also contain zero of the
required injection, secret-bait, negative-evidence, one-based, homoglyph or
zero-width security cases. No exposed diagnostic label was accessed.

Result: `CANDIDATE_TEXT_SHORTCUT_LEAKAGE`, independently blocking a credible
binary training claim.
