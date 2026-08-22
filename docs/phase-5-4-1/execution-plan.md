# Frozen Phase 5.4.1 execution plan

Status: **FROZEN BEFORE REAL TRAINING**

The plan is bound to the official Qwen3.5-2B revision and the Phase 5.3
real-world T1 development corpus. The only allowed real run is:

```text
runId: P5.4-T1-real-world-r8
precision: BF16_LORA
allocator: PYTORCH_CUDA_ALLOC_CONF=expandable_segments:True
rank/alpha/dropout: 8 / 16 / 0.05
learning rate: 1e-4
epochs: 6
batch/accumulation: 1 / 16
gradient checkpointing: enabled, use_reentrant=false
maximum sequence length: 2048
seed/data seed: 520021
```

The target modules are exactly those selected from the inspected architecture:

```text
down_proj, gate_proj, in_proj_a, in_proj_b, in_proj_qkv, in_proj_z,
k_proj, o_proj, out_proj, q_proj, up_proj, v_proj
```

The full run must use 140 TRAIN rows and 40 VALIDATION rows, the hashes in the
machine-readable plan, and fresh external output directories. It must not use
`resume_from_checkpoint`, load validation during optimizer steps for selection,
or load any sealed-final case.

After training, every saved checkpoint may be evaluated only on VALIDATION.
Exactly one adapter is selected using the prescribed validation-only order. A
selection document and commit are required before any locked-final import.
Final access remains closed unless the selection receipt, adapter hash, and
final descriptor hash pass fail-closed checks.

The memory-attribution report is SHA-256
`7979f032e8e417d3a81858b04375f6dcfb239d87718aacf52de888ff1995a863`.
Machine-readable plan: `research/model-alignment/reports/phase-5-4-1-execution-plan.json`.
Plan SHA-256: `810180a2ac13197b2d6645ac143a8902563e0d2dc35a4ea7f4034887da66f7b0`.
