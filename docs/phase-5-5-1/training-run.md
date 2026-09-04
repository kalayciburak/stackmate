# Phase 5.5.1 training run

Status: `NOT_RUN`.

The single allowed run remained predeclared and unopened:

```text
Base                  official Qwen3.5-2B revision
Precision             BF16 LoRA
Rank / alpha           8 / 16
Dropout                0.05
Learning rate          1e-4
Batch / accumulation   1 / 16
Maximum epochs         4
Seed                   520055
Initialization         official base only
Target                 exactly one label token
```

Gate B failed before Gate C, so there is no run ID, optimizer step, loss, VRAM
measurement, checkpoint, adapter directory or adapter hash. No second seed,
rank-16 run, prompt search, merge or GGUF work occurred.
