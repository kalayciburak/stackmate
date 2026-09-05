# Phase 5.5 reproducibility

Phase 5.5 is bound to branch `phase-5-5-pairwise-evidence-entailment`, starting
commit `fc34177e6fb7acc1a9e5ebf3dfdcf1ff29439e0b`, and local annotated tag
`phase-5-4-3-complete`. No historical output was overwritten.

Frozen inputs:

```text
Base revision             15852e8c16360a2fea060d615a32b45270f8a8fc
Base manifest SHA-256     1dc49db3fd0a49ee7bf33190b04c1bac5d1ed489e87855a81c64ac7344571981
TRAIN SHA-256             ee52ae93457b9dc7f663f83b1acea37df6c25f437ac84261c951844a5965ba93
VALIDATION SHA-256        bc8e2b5573c57f6927f0e8547968e29fa3bf0f24b221d7bb402b13ba04680789
Dataset manifest SHA-256  0e2e23241b582fb4f7c5fb25b87a5f08016f28f8be0c7525595e170467f5d2b2
Prompt SHA-256            93515b517ff08c5bcf92764936e62a4c8709652fe0ff14152fbe0f6fc3a96517
Chat-template SHA-256     273d8e0e683b885071fb17e08d71e5f2a5ddfb5309756181681de4f5a1822d80
Label-mapping SHA-256     98b223427fc6be46cedbe74fd3e10c9696900fba59f032387d63288925e2de76
Source-audit SHA-256      cb9f4d734542c361ff76c7eaa90b261d308fe8aff093f0a9150cc7bb1fda413c
```

Authoritative source audit command, run in WSL Ubuntu with the existing frozen
environment:

```bash
UV_PROJECT_ENVIRONMENT='<external-training-cache>/<phase-subdir>' \
uv run --frozen python scripts/audit_pairwise_source.py \
  --report reports/phase-5-5-pairwise-source-audit.json
```

The expected nonzero exit is the gate result
`FAIL_CONTRADICTED_CLASS_ABSENT`, not a crash. The report uses create-new writes,
contains no timestamp or machine path, rejects duplicate JSON keys, verifies the
frozen manifest hashes, validates both order variants, and emits only aggregate
exposed-overlap counts.

A prior PowerShell command accidentally resolved `$HOME` to the Windows profile
and created `<external-training-cache>/<phase-subdir>` with the
same frozen project dependencies. Its audit report was removed and regenerated
under WSL; the Windows environment is not authoritative, contains no model or
dataset copy, and remains only because recursive cleanup was policy-rejected. At
final inspection it contained 20,286 files and 759,475,192 bytes (724.29 MiB).

Final verification:

```text
uv lock --check                         PASS, 64 packages resolved
python -m py_compile scripts/*.py ...  PASS
python -m unittest discover -s tests  PASS, 24/24
mvnw clean verify                      PASS, 517 Surefire + 11 Failsafe
Spotless check                         PASS
JaCoCo/Enforcer gates                  PASS
```

The first final Maven invocation exited before project execution because the
profile-free PowerShell process had no `JAVA_HOME`. The identical command was
rerun with process-local `JAVA_HOME` pointing to
`<jdk-21-home>`; no persistent environment
setting changed.
