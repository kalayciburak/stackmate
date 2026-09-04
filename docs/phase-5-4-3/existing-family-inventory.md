# Existing exposed-family inventory

The Phase 5.4.3 inventory normalizes every family or adversarial fixture exposed
through Benchmark V2, screening, blind qualification, Phase 5.1 prompt studies,
Phase 5.2 SFT/challenge data, and the Phase 5.3/5.4 real-world corpus.

| Catalog | Records |
|---|---:|
| Benchmark V2 | 80 |
| Phase 4 screening aliases | 20 |
| Phase 5 blind | 20 |
| Phase 5.1 adversarial prompts | 8 |
| Phase 5.2 synthetic SFT families | 40 |
| Post-training challenge | 30 |
| Real-world v1 families | 120 |
| **Total records** | **318** |
| **Unique canonical family IDs** | **298** |

For each record the inventory stores normalized recipe, throwable-chain,
root-cause, evidence, cause-candidate, check-candidate, dependency-topology,
classification, and exposure signatures. Volatile numbers, UUIDs, and private
paths are normalized before hashing.

Key exposure counts:

```text
Model evaluation: 178
Real-world training: 70
Real-world validation: 20
Historical locked-final descriptors: 30
Synthetic SFT training/validation: 32/8
```

Inventory SHA-256:
`cb4c52669c5369b8443482400311bc0fb578d2f9640ef9bfdb6cca39705467a3`.

The complete machine-readable inventory is
`research/model-alignment/reports/phase-5-4-3-existing-family-inventory.json`.
