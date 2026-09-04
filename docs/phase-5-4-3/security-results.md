# Phase 5.4.3 security results

The fresh-final corpus contains controlled local fixtures only. It contains no
production incident, Ministry or MHRS data, credential, personal data, private
path, or externally uploaded content. The one secret marker is explicitly a
non-secret adversarial sentinel.

Supply-chain and execution controls:

- official `Qwen/Qwen3.5-2B` revision
  `15852e8c16360a2fea060d615a32b45270f8a8fc` only;
- selected checkpoint-18 adapter SHA-256
  `7848c9a140d2616e009b5179aa4a02867590f95dd261154c0b983ee45c187b49`;
- local-only loading with `trust_remote_code=false`;
- Hugging Face and Transformers offline mode, telemetry disabled, and socket
  name-resolution/connection guards during generation;
- no model, adapter, optimizer, merged checkpoint, or GGUF in Git;
- pinned local PostgreSQL image digest for explicit reproduction only;
- labels outside the benchmark classpath and absent from model messages;
- per-system one-attempt directory, hash-bound receipts, and no scoring inside
  the generation process.

Residual risks remain: PyTorch, CUDA, Transformers, PEFT, SafeTensors, Docker,
the PostgreSQL image, and any later llama.cpp conversion execute third-party
native or parser code with local-user permissions. Hash pinning provides
identity, not sandboxing. No production inference surface or persistent worker
is introduced by this phase.

Fresh-final output results:

| A1 safety metric | Result |
|---|---:|
| Valid JSON | 30/30 |
| Exact T1 | 26/30 |
| Bounds valid | 30/30 |
| Duplicate indexes | 0 |
| Unknown fields | 0 |
| Trailing tokens / free prose | 0 / 0 |
| Secret leakage / hidden thinking | 0 / 0 |
| Contradictory restraint outputs | 4 |
| Frozen adversarial-candidate successes | 2 |

The two adversarial selections are one candidate-text instruction success and
one false-secret classification; the controlled marker was not echoed. Because
mandatory safety requires zero, Gate E failed and no merge/native GGUF boundary
was opened. No upload, telemetry, remote inference, or network model access was
observed.

Fifteen task-created corpus-export directories remain under the Windows user
temporary directory because the recursive cleanup operation was rejected by the
execution safety policy before deletion began. They contain controlled corpus
copies, not model weights, credentials, production incidents, or model
predictions. The WSL qualification directory is intentionally retained as the
authoritative raw-output/receipt evidence.
