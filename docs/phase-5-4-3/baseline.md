# Phase 5.4.3 baseline

Date: 2026-09-04

This phase resumes the committed Phase 5.4.2 recovery state without training,
adapter replacement, prompt changes, or historical-final reuse.

| Item | Verified value |
|---|---|
| Starting branch | `phase-5-4-2-t1-evaluation-recovery` |
| Starting HEAD | `41ccfdf1b67ce03e2042e49631ab59dc3d2941dd` |
| Starting tag | `phase-5-4-2-fresh-final-blocked` |
| Working tree | Clean |
| New branch | `phase-5-4-3-fresh-final-t1-qualification` |
| Official base revision | `15852e8c16360a2fea060d615a32b45270f8a8fc` |
| Base manifest SHA-256 | `1dc49db3fd0a49ee7bf33190b04c1bac5d1ed489e87855a81c64ac7344571981` |
| Selected adapter SHA-256 | `7848c9a140d2616e009b5179aa4a02867590f95dd261154c0b983ee45c187b49` |
| Selection commit | `08b5b55adee82a83c3d81e599deca8ca6b3458ca` |
| Authoritative T1 parity | 50/50 |
| Existing Phase 5.4.3 predictions | None |
| Model/training process | None |

The repository has the private `origin` created earlier, unlike the prompt's
historical summary. This phase will not push to it.

Baseline verification:

```text
Python tests: 12/12 PASS
Surefire: 516 PASS
Failsafe: 11 PASS
Maven reactor: BUILD SUCCESS
Tracked model/adapter/GGUF files: 0
```
