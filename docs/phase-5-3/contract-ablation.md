# Phase 5.3 model-facing contract ablation

Date: 2026-08-22

## Development-only results

All scores use the 40-case frozen parity corpus, P3, greedy generation, seed 42,
maximum 192 output tokens, official local tokenizer, and no repair. Ordinal indexes
are zero-based and request-local.

| Model/path | Contract | Order | Valid | Cause | Check | Restraint | Unknown refs |
|---|---|---|---:|---:|---:|---:|---:|
| BF16 base | C0 opaque IDs + prose | original | 28/40 | 25/40 | 30/40 | 8/8 | 19/96 |
| BF16 base | C0 opaque IDs + prose | permuted | 27/40 | 25/40 | 30/40 | 8/8 | 20/93 |
| F1 | C0 opaque IDs + prose | original | 31/40 | 26/40 | 26/40 | 8/8 | 18/90 |
| F1 | C0 opaque IDs + prose | permuted | 29/40 | 25/40 | 25/40 | 8/8 | 22/92 |
| BF16 base | C1 ordinal IDs + prose | original | 15/40 | 16/40 | 15/40 | 0/8 | 0 |
| F1 | C1 ordinal IDs + prose | original | 19/40 | 16/40 | 16/40 | 0/8 | 0 |
| BF16 base | C2 per-candidate decisions | original | 23/40 | 20/40 | 19/40 | 0/8 | 0 |
| F1 | C2 per-candidate decisions | original | 0/40 | 0/40 | 0/40 | 0/8 | 0 |
| BF16 base | T1 ordinal selection only | original | 40/40 | 38/40 | 38/40 | 8/8 | 0 |
| BF16 base | T1 ordinal selection only | permuted | 40/40 | 34/40 | 34/40 | 8/8 | 0 |
| F1 | T1 ordinal selection only | original | 30/40 | 30/40 | 29/40 | 0/8 | 0 |
| BF16 base | T2 immutable selections + prose only | original | 33/40 | immutable | immutable | 4/8 | 0 |
| F1 | T2 immutable selections + prose only | original | 40/40 | immutable | immutable | 8/8 | 0 |

## Candidate-order bias

| Path/contract | Selection invariance | Accuracy change |
|---|---:|---|
| BF16 C0 | 32/40 | cause/check unchanged at 25/40 and 30/40 |
| F1 C0 | 36/40 | cause/check fell from 26/40 to 25/40 |
| BF16 T1 | 36/40 | cause/check fell from 38/40 to 34/40 |

The model is order-sensitive, but order does not explain most C0/F1 failures. The
original frozen corpus also places every non-restraint expected cause/check at the
first candidate position, so future data must randomize order by construction.

## Responsibility ablation

T1 was the only contract that simultaneously achieved:

- 40/40 valid bounded output;
- zero unknown references;
- 8/8 strict restraint in both original and permuted order;
- at least 85% cause/check accuracy after deterministic permutation;
- short output: mean 77.25 characters;
- mean BF16 generation latency about 0.60 seconds.

T1 asks the model only for local cause/check indexes and a limitation. Java maps
indexes back to request-scoped IDs and renders existing deterministic cause/check
text. It does not ask the model to author explanations.

## Prose-only manual review

F1 T2 produced 40/40 structurally valid prose-only JSON, but two complete manual
passes found:

| Metric | Conservative result |
|---|---:|
| Useful | 33/40 (82.5%) |
| Adds clarity beyond deterministic text | 0/40 |
| Confusion | 7 |
| Unsupported technical claims | 4 |
| Repetition | 0 |

Examples included an unsupported missing-table claim, a false missing-JDK-class
explanation for DNS failure, and unsupported “missing field/arguments” details.
Model-prose-only is rejected despite perfect structural compliance.

## Decision

```text
SEPARATE_SELECTION_AND_PROSE
```

For a future experiment, use T1 ordinal selection-only output and deterministic
Java rendering. Do not change the production schema in Phase 5.3. F1 remains a C0
format adapter and is not selected as the T1 execution model.
