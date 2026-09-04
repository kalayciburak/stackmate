# Phase 5.5 training run

Status: `NOT_RUN`.

The single authorized run remained predeclared but unopened:

```text
Base               Qwen/Qwen3.5-2B official revision
Precision          BF16 LoRA
Rank / alpha        8 / 16
Dropout             0.05
Learning rate       1e-4
Maximum epochs      4
Gradient checkpointing enabled when needed
Seed                520055
Initialization      official base, never the listwise adapter
Target              exactly one label token; prompt masked
```

Gate C was unavailable after Gate B failed, so there is no run ID, optimizer
step, loss, VRAM measurement, adapter directory, checkpoint, or adapter hash.
No second seed, rank-16 run, full-parameter run, merge, or GGUF export occurred.
