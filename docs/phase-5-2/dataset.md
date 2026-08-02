# Phase 5.2 alignment dataset

Date: 2026-08-02

## Result

Gate A dataset feasibility is `PASS`. The locked dataset contains 1,000
deterministically generated, English, schema-v2 chat examples. It contains no
model-generated target and no private incident data.

| Property | Value |
|---|---:|
| Seed | `520021` |
| Train rows | 800 |
| Validation rows | 200 |
| Train families | 32 |
| Validation families | 8 |
| Supported selection | 450 (45%) |
| Hard negative | 250 (25%) |
| Strict abstention | 250 (25%) |
| Security/delimiter | 50 (5%) |

Validation holds out 8/40 template families, or 20%. No family crosses the
train/validation boundary. All rows are controlled deterministic synthetic
variations. Benchmark V2 development cases and analyzer fixtures were not copied
into v1; omitting them keeps the first feasibility run maximally auditable and
avoids accidental locked-text reuse.

## Contract

Each row contains exactly three messages:

1. stable T1 closed-world system instruction;
2. schema-v2 structured incident inside the production untrusted-data delimiter;
3. one compact schema-v2 assistant JSON object.

Targets select only supplied IDs. Abstention targets use empty cause, check, and
evidence arrays plus `EVIDENCE_INSUFFICIENT`. The dataset contains no chain of
thought, source-inspection claim, real secret, private path, PII, production log,
Ministry data, or MHRS data.

## Locked artifacts

| Artifact | SHA-256 |
|---|---|
| Dataset manifest | `b433fd0091594570aaf8a973b7bbc0b9eb77dca5682b01d010ff8071c73ea2f2` |
| Train JSONL | `4c3d7675666212406400884a415a2920be6da36a80c617b55e31896f820cf070` |
| Validation JSONL | `4b3d9bb0cd4dcbc79c1764ae2a511e1f2eb007f375cf72b8d5cb193eecef507b` |
| T1 prompt | `07713df9cb6c7a12dd1eb9d0a047d3e6acb87c1848ab645c17652e904c9707b7` |
| Leakage audit | `49aaaf88eb8bd3d81fb1bb0329eb0332ba78406c5c3cf9a3522e3692771d712b` |

The generator records that it does not read locked corpora. The audit is a
separate process and records the frozen source checksums it inspected.

## Commands and actual results

```powershell
cd research/model-alignment
python scripts/build_dataset.py --config configs/dataset-v1.json
python scripts/audit_leakage.py --config configs/dataset-v1.json
python -m unittest discover -s tests -v
```

Results: generation `PASS` with 800/200 rows; leakage audit `PASS` with zero
prohibited overlap; five Python contract tests passed.

```powershell
$env:JAVA_HOME='C:\Users\Burak\.jdks\corretto-21.0.12'
.\mvnw -pl stackmate-benchmark -am "-Dtest=AlignmentDatasetManifestTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: four Java fixture tests passed with zero failures/errors/skips. Normal
Java validation used no Python package, model file, native runtime, network, or
secret.

## Pre-lock corrections

The first generator run rejected rows whose only variation was numeric because
volatile-number normalization collapsed them to exact duplicates. Stable
alphabetic variation keys replaced those values.

The first leakage run rejected 30 instances of one repeated distractor phrase:
`Change the column type` exactly matched a forbidden challenge phrase. The
phrase was removed, all files were regenerated, and the stricter field-level
audit then passed. No model or adapter output was available during either
correction.

Tokenizer/chat-template length statistics remain a Gate B environment check and
are not claimed by Gate A.
