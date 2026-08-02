# StackMate model-alignment research

This directory isolates Phase 5.2 data, training, and evaluation research from
the Maven product modules. Normal Java CI reads only the committed dataset
fixtures and downloads no model or Python environment.

## Dataset commands

From this directory:

```powershell
python scripts/build_dataset.py --config configs/dataset-v1.json
python scripts/audit_leakage.py --config configs/dataset-v1.json
python -m unittest discover -s tests -v
```

`build_dataset.py` has no path or import for a locked evaluation corpus. The
separate audit script may read the locked manifests and fixture sources only to
reject overlap; it never emits their text into the generated training rows.

The committed `datasets/lock.json` is the training boundary. Any generator,
prompt, dataset, audit, or challenge change invalidates a checksum and must be
reviewed before an adapter run.
