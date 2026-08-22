# Phase 5.2 GGUF compatibility

Date: 2026-08-02

Gate D is `NOT_RUN` because Gate C failed. No merge, export, quantization, or
GGUF compatibility subset was attempted after the locked result.

| Artifact/result | Value |
|---|---|
| Selected adapter | F1 locked locally; export not authorized by failed Gate C |
| Merged model checksum | NOT_AVAILABLE |
| Fine-tuned GGUF checksum | NOT_AVAILABLE |
| Quantization | Q4_K_M planned, not executed |
| 20-case compatibility subset | NOT_RUN |
| Behavioral drift | NOT_MEASURED |
| Latency / RSS | NOT_MEASURED |

The existing 1,396,198,496-byte Q4_K_M file is the unchanged Phase 5 base
artifact, not a Phase 5.2 output. Metadata inspection identified architecture
`qwen35`; Transformers 5.14.1 does not support loading that GGUF architecture for
training. No merge, conversion, quantization, or llama.cpp compatibility run was
attempted.
