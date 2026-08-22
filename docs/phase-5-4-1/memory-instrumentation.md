# Stage-scoped memory instrumentation

Each stage ran in a fresh WSL process with the official local checkpoint,
`local_files_only=true`, `trust_remote_code=false`, BF16, and no LOCKED_FINAL
input. The process used `nvidia-smi` sampling every 250 ms and captured
PyTorch allocator statistics after each stage.

| Stage | Scope |
|---|---|
| M0 | Base BF16 model load only |
| M1 | Base BF16 model plus rank-8 LoRA attachment |
| M2 | 16 longest TRAIN rows, exactly 10 BF16 optimizer steps, adapter save; no validation or reload |
| M3 | Fresh base plus adapter reload and one TRAIN-row JSON probe |
| M4 | Fresh base plus adapter reload and VALIDATION-only JSON probes |

Captured fields include current and peak allocated/reserved bytes, device free
and total bytes, reserved-minus-allocated, active bytes, inactive split bytes,
requested bytes, allocation retries, OOM count, process RSS, available system
RAM, the available `memory_stats()` keys, and the external `nvidia-smi` trace.

Raw artifacts remain outside Git under:

```text
$HOME/.cache/stackmate/phase-5-4-1-memory/
$HOME/.cache/stackmate/phase-5-4-1-memory-expandable/
```

The repository contains sanitized stage summaries and SHA-256 values. The
reports never loaded sealed-final data and never perform network access.
