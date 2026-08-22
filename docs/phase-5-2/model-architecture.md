# Phase 5.2 model architecture

Date: 2026-08-02

The report is based on the exact local Qwen3.5-2B snapshot, not on GGUF metadata.
Loading used `local_files_only=true` and `trust_remote_code=false`.

| Item | Observed value |
|---|---|
| Model class | `Qwen3_5ForConditionalGeneration` |
| Model type | `qwen3_5` with `qwen3_5_text` language component |
| Parameter count | 2,213,241,664 |
| Text hidden size | 2,048 |
| Text layers | 24 |
| Context length | 262,144 declared; experiment sequence limit 2,048 |
| Attention layout | 18 linear-attention layers and 6 full-attention layers |
| Architecture report | `research/model-alignment/reports/model-architecture.json` |

## Selected LoRA targets

Targets were selected from the inspected text-model module tree. Visual modules,
embeddings, and `lm_head` are excluded.

```text
down_proj
gate_proj
in_proj_a
in_proj_b
in_proj_qkv
in_proj_z
k_proj
o_proj
out_proj
q_proj
up_proj
v_proj
```

Observed text projection suffix counts were: `down_proj`/`gate_proj`/`up_proj`
24 each; `in_proj_a`/`in_proj_b`/`in_proj_qkv`/`in_proj_z`/`out_proj` 18 each;
and `k_proj`/`o_proj`/`q_proj`/`v_proj` 6 each. The selected target list and
sample names are recorded in the machine-readable architecture report.

The target-list report SHA-256 used by both runs is
`bbc77177898f6de14dd26f96f7e6315c52b7c9fc16ee0b0ab13510f20909810f`.
