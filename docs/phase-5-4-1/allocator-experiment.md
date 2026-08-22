# Allocator experiment

Baseline M2 reached 17,402,167,296 reserved bytes while its post-step active
allocation was 4,495,191,040 bytes. It reached zero PyTorch-reported free
device bytes and only 87 MiB minimum free in the external trace. The baseline
inactive split was 70,308,864 bytes, so the evidence was recorded as allocator
reservation behavior rather than an intrinsic model-quality conclusion.

Exactly one mitigation was tested in a separate M0-M4 series:

```text
PYTORCH_CUDA_ALLOC_CONF=expandable_segments:True
```

No `empty_cache()` workaround, `max_split_size_mb`, sequence cap, precision
change, or second mitigation was used. Expandable M2 reserved 8,833,204,224
bytes, retained 6,921,650,176 PyTorch free bytes after training, and had zero
inactive split bytes, retries, and OOMs. The mitigation therefore qualifies
BF16 for the frozen execution plan. It does not constitute a training-quality
result.
