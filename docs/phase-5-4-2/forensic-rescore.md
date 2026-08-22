# Forensic historical-output rescore

No model was invoked. The original raw report and receipt hashes were verified
before classification.

| Classification | Count |
|---|---:|
| Valid T1 | 0 |
| Valid schema-v2 shape | 119 |
| Valid both | 0 |
| Invalid both | 146 |
| Truncated | 12 |
| Unknown format | 3 |

For A1 and A2 specifically:

```text
A1 valid T1: 0/70
A2 valid T1: 0/70
A1 valid strict schema-v2 shape: 1/70
A2 valid strict schema-v2 shape: 1/70
```

The original A1/A2 outputs are therefore not salvageable as T1 semantic
predictions. Their field signatures are useful diagnostic evidence showing the
model followed the schema-v2-like locked prompt path, but no index mapping is
performed from incompatible ID output.

Classification: `CONTRACT_CONTAMINATED_FINAL`, diagnostic-only. The original
raw artifacts remain historical and are not overwritten.
