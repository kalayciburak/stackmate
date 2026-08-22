# T1 versus schema-v2 mismatch root cause

## T1 validation path

The Phase 5.4.1 training rows embed a system message whose contract is
explicitly ordinal T1:

```text
Contract T1 uses zero-based local indexes.
Required exact JSON shape:
{"selectedCauseIndexes":[0],"selectedCheckIndexes":[0],"limitation":null}
```

The T1 system prompt SHA-256 is
`9daee6dc707ff1c347346f5f0c667cba2b03035ab3966ddaf362744e0c528897`.
`evaluate_real_world_f1.py` validates the three T1 fields through
`EXPECTED_KEYS` and `score`; `evaluate_phase54_t1.py` uses the same T1 path.

## Original locked path

`run_locked_evaluation.py` defines the four conditions as:

```text
B0 -> P0
B1 -> P3
A1 -> P3
A2 -> P2
```

The Phase 5.4.1 runner preserved that mapping. It did not import the T1 system
prompt for A1/A2. P3 and P2 are schema-v2 prompts requiring:

```text
schemaVersion, summary, explanation, selectedCauseIds,
selectedCheckIds, usedEvidenceIds, limitations
```

Prompt hashes:

```text
P0: 3362afedc99b7c930a8423a33d011bb8b8a026d7072befef6041f6629b36fa03
P2: 9f40df4491fcb5a009b05d94c505bb4f1105e34f0fd48c7231bbae78fdd7e213
P3: d5117da41bd8af64982035fb75dbac91968249ea48fd02f120c532755074bf7f
```

The Python schema-v2 scorer requires the seven ID/prose fields. The Java
`ScoreLockedEvaluation` path parses `StructuredInferenceOutput` and invokes
`DefaultInferenceOutputValidator`; it never invokes the T1 ordinal parser.

## Exact boundary

The mismatch entered at the condition-to-prompt mapping in the locked runner,
combined with the absence of one shared authoritative T1 contract module.
The adapter therefore received P3/P2 instructions and generated mostly
schema-v2-like ID fields. It did not generate T1 ordinal fields in the original
A1/A2 output. The Java scorer then rejected incomplete schema-v2 outputs.

This is an evaluation-contract contamination, not evidence that the adapter's
T1 semantic selection accuracy was zero.
