# Phase 5.5.2 training run

Status: NOT_RUN.

Gate E failed before training. The one authorized configuration therefore
remained unopened:

~~~text
Base                  official Qwen3.5-2B
Precision             BF16 LoRA
Rank / alpha           8 / 16
Dropout                0.05
Learning rate          1e-4
Batch / accumulation   1 / 16
Maximum sequence       1024
Maximum epochs         4
Seed                   520055
Initialization         official base only
~~~

There is no run ID, optimizer step, loss, training checkpoint, peak training
VRAM, adapter directory or adapter checksum. No alternate seed, rank, learning
rate, prompt or dataset was attempted.
