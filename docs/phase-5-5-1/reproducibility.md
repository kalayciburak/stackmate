# Phase 5.5.1 reproducibility

Phase 5.5.1 starts from commit
`5585a59c36d68fab45887688453d6b19b9ec7a8f` and local annotated tag
`phase-5-5-complete` on branch `phase-5-5-1-binary-evidence-support`.

Frozen inputs:

```text
Base revision             15852e8c16360a2fea060d615a32b45270f8a8fc
Base manifest SHA-256     1dc49db3fd0a49ee7bf33190b04c1bac5d1ed489e87855a81c64ac7344571981
TRAIN SHA-256             ee52ae93457b9dc7f663f83b1acea37df6c25f437ac84261c951844a5965ba93
VALIDATION SHA-256        bc8e2b5573c57f6927f0e8547968e29fa3bf0f24b221d7bb402b13ba04680789
Tri-class audit SHA-256   cb9f4d734542c361ff76c7eaa90b261d308fe8aff093f0a9150cc7bb1fda413c
Binary prompt SHA-256     fc4c3569a17ab5b902b88c6105f5b0d7587087eabd129b779395c7400ce01ddb
Binary mapping SHA-256    fa98365a55f91f7902890b4fad5b034d945f9bacaa43d1ec90d70a59af3873d8
Token audit SHA-256       da03c29aba32416ad81fa14413861751ea58f1a5c9b7d2d9c1dab150128a962e
Source audit SHA-256      18a489fbcd86f41e9d8d8e111fd192a0a1e1e336452e1286b37ece5ae6db6c7d
```

Baseline verification:

```text
uv lock --check                         PASS, 64 packages
python -m py_compile scripts/*.py ...  PASS
python -m unittest discover -s tests  PASS, 24/24 before binary additions
mvnw clean verify                      PASS, 517 Surefire + 11 Failsafe
Spotless / coverage / Enforcer         PASS
```

The binary label audit used the existing WSL environment and exact local model
directory with `local_files_only=true`; it loaded the tokenizer only. The source
audit is deterministic, reads canonical variant zero, verifies input hashes and
must return a nonzero methodological gate status without creating a dataset when
VALIDATION class coverage is insufficient.

Authoritative source-audit command:

```bash
UV_PROJECT_ENVIRONMENT='<external-training-cache>/<phase-subdir>' \
uv run --frozen python scripts/audit_binary_source.py \
  --report reports/phase-5-5-1-binary-source-audit.json
```

It returned `BINARY_VALIDATION_CLASS_INSUFFICIENT`, created the aggregate report,
and wrote no dataset. The report is deterministic, timestamp-free, path-free,
duplicate-key safe, bound to the tri-class audit, and contains 90 hashed
family-level reviews without individual labels.

A second create-new execution wrote to an external WSL temporary path and
produced the identical report SHA-256. Final research verification passed:

```text
uv lock --check                         PASS, 64 packages
python -m py_compile scripts/*.py ...  PASS
python -m unittest discover -s tests  PASS, 36/36
Binary contract tests                  PASS, 5/5
Binary source-audit tests              PASS, 7/7
```

The normal Maven verification was already completed after the Phase 5.5.1
branch point and before research-only Python/documentation changes: 517 Surefire
and 11 Failsafe tests passed with zero failures, errors or skips. No Java or POM
file changed afterward.
