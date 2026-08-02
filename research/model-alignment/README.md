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

## Environment and training boundary

Create the pinned WSL2 environment outside the repository, then run the
offline preflight:

```bash
UV_PROJECT_ENVIRONMENT="$HOME/.cache/stackmate-phase5-2-venv" uv sync --frozen
UV_PROJECT_ENVIRONMENT="$HOME/.cache/stackmate-phase5-2-venv" \
  uv run python scripts/environment_preflight.py \
  --config configs/environment-v1.json \
  --output reports/environment-preflight.json
```

`STACKMATE_BASE_MODEL_DIR` must point to a complete local checkout of the exact
official revision pinned in `environment-v1.json`, using the immutable
Hugging Face cache snapshot path whose directory name is that revision. The
preflight verifies the tokenizer files and every safetensors shard referenced
by the index. It is offline and fails closed when that checkout is absent. The
existing third-party Q4_K_M GGUF is an inference artifact and is not accepted
as the official training base.

The two bounded QLoRA plans are in `configs/qlora-r8.json` and
`configs/qlora-r16.json`. Their target modules must come from
`reports/model-architecture.json`, produced only after the official model can
be loaded and inspected. No training command is provided while that artifact
is absent; this prevents an unverified architecture guess from becoming a
training run.
