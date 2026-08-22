# Authoritative T1 contract

The recovery adds `research/model-alignment/scripts/t1_contract.py` and a
matching Java `T1Contract.java` implementation.

Canonical output:

```json
{"selectedCauseIndexes":[0],"selectedCheckIndexes":[0],"limitation":null}
```

Rules are frozen as:

- exactly three fields;
- zero-based integer indexes;
- no duplicate indexes;
- negative and out-of-bounds indexes invalid;
- `limitation` is `null` for supported selection;
- empty selections require `EVIDENCE_INSUFFICIENT`;
- `EVIDENCE_INSUFFICIENT` requires empty cause and check arrays;
- no prose, evidence IDs, persistent IDs, or schema-v2 fields.

The Python and Java modules expose identical parse failure codes and score
fields. Cross-language golden fixtures are required before any fresh model
execution.
