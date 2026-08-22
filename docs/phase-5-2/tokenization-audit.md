# Phase 5.2 tokenization audit

Date: 2026-08-02

The frozen 800/200 dataset was audited with the official tokenizer and chat
template from the immutable base snapshot. No semantic dataset content or locked
evaluation case was changed.

| Split / measure | Input tokens | Output tokens | Total sequence |
|---|---:|---:|---:|
| Train average | 359.98875 | 55.63125 | 415.62000 |
| Train P95 | 378 | 57 | 434 |
| Train maximum | 389 | 58 | 443 |
| Validation average | 361.30000 | 56.02500 | 417.32500 |
| Validation P95 | 379 | 57 | 436 |
| Validation maximum | 383 | 57 | 437 |

## Checks

| Check | Result |
|---|---|
| Sequence limit | 2,048; no truncation |
| Unknown-token behavior | 0 unknown tokens |
| Duplicate BOS/EOS | none detected |
| Padding | right; pad ID 248044 (`<|endoftext|>`) |
| EOS token | ID 248046; template examples do not end with EOS text |
| Assistant boundary | verified for all 1,000 examples |
| Prompt labels | 360,251 masked prompt tokens; 55,710 target tokens |
| Chat template | official local template; no duplicated special tokens |

The audit is recorded in `research/model-alignment/reports/training-format.json`.
