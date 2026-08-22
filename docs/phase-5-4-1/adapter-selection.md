# Phase 5.4.1 adapter selection

Status: **SELECTED FROM VALIDATION ONLY**

The single authorized real training run produced six epoch checkpoints. Each
checkpoint was evaluated only on the frozen 40-row VALIDATION split with the
official BF16 base and deterministic generation. The selection order was:

1. zero validation unsupported/unknown claims;
2. strict restraint;
3. zero generic fallback misuse;
4. cause accuracy;
5. check accuracy;
6. valid structured output;
7. order invariance and lower confusion where available;
8. smaller adapter.

| Checkpoint | Cause | Check | Joint | Restraint | Fallback misuse | Unknown IDs | Valid JSON | Order |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 9 | 38/40 | 37/40 | 37/40 | 22/22 | 0 | 0 | 40/40 | 18/20 |
| **18 selected** | **39/40** | **40/40** | **39/40** | **22/22** | **0** | **0** | **40/40** | **19/20** |
| 27 | 38/40 | 37/40 | 37/40 | 22/22 | 0 | 0 | 40/40 | 18/20 |
| 36 | 37/40 | 36/40 | 36/40 | 22/22 | 0 | 0 | 40/40 | 18/20 |
| 45 | 38/40 | 37/40 | 37/40 | 22/22 | 0 | 0 | 40/40 | 18/20 |
| 54 | 38/40 | 37/40 | 37/40 | 22/22 | 0 | 0 | 40/40 | 18/20 |

Selected adapter:

```text
Adapter ID: P5.4.1-T1-checkpoint-18
Base: Qwen/Qwen3.5-2B@15852e8c16360a2fea060d615a32b45270f8a8fc
Path: <WSL_CACHE>/adapters/phase-5-4-1/selected-P5.4-T1-real-world-r8
SHA-256: 7848c9a140d2616e009b5179aa4a02867590f95dd261154c0b983ee45c187b49
Size: 53,704,036 bytes
Source checkpoint directory SHA-256: fd136663aed59a4d9487fc276e85e1d3a24ddc13dfa5e037ecfe59868f733bb9
```

The selected copy contains adapter and tokenizer files only; optimizer state,
RNG state, scheduler state, and trainer state remain in the external training
checkpoint directory. The machine-readable binding is
`research/model-alignment/reports/phase-5-4-1-selected-adapter.json`.
