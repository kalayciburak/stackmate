# Phase 5.2 checkpoint acquisition

Date: 2026-08-02

## Result

The exact official Transformers checkpoint was acquired after the historical Gate B
block. The source is the official [`Qwen/Qwen3.5-2B`](https://huggingface.co/Qwen/Qwen3.5-2B)
repository at immutable revision
`15852e8c16360a2fea060d615a32b45270f8a8fc`. No mirror, mutable branch, GGUF,
PyTorch-bin duplicate, remote inference, or remote-code execution was used.

| Item | Value |
|---|---|
| Resolved WSL snapshot | `$HOME/.cache/stackmate/models/models--Qwen--Qwen3.5-2B/snapshots/15852e8c16360a2fea060d615a32b45270f8a8fc` |
| Downloaded files | 10 |
| Total size | 4,571,198,095 bytes |
| Download date | 2026-08-02 UTC |
| huggingface_hub | 1.26.0 |
| Maximum workers | 2 |
| Partial files | 0 |
| GGUF/bin/ONNX/Flax/TensorFlow duplicates | 0 |
| Training network fallback | disabled |

## SHA-256 manifest

| File | Bytes | SHA-256 |
|---|---:|---|
| `chat_template.jinja` | 7,755 | `273d8e0e683b885071fb17e08d71e5f2a5ddfb5309756181681de4f5a1822d80` |
| `config.json` | 2,908 | `ed1c1723241f23f7f4e23430759cbd7dcfb4103cbdfe052bfe7626b57c2615b4` |
| `merges.txt` | 3,353,259 | `a9d356d7bdf1ef4949e3e748e95b8e10ad9d4e2e838eddc38a0a7b6b94d1db8d` |
| `model.safetensors-00001-of-00001.safetensors` | 4,548,221,488 | `aa33250c4fc64891ddfaba3a314fd9542ea371843c387178b425fbcc5ed680b1` |
| `model.safetensors.index.json` | 64,460 | `aca8afed9da75b0f050b408d270766fd77627f1af401e240f61c3b47d0db02f9` |
| `preprocessor_config.json` | 390 | `27225450ac9c6529872ee1924fcb0962ff5634834f817040f444118116f4e516` |
| `tokenizer.json` | 12,807,982 | `5f9e4d4901a92b997e463c1f46055088b6cca5ca61a6522d1b9f64c4bb81cb42` |
| `tokenizer_config.json` | 16,709 | `49e2b6e395f959f077f1e992b338919c0d4a9732fc6e613995e06557f843500c` |
| `video_preprocessor_config.json` | 385 | `7768af27c1fafa9cc9011c1dc20067e03f8915e03b63504550e11d5066986d13` |
| `vocab.json` | 6,722,759 | `ce99b4cb2983d118806ce0a8b777a35b093e2000a503ebde25853284c9dfa003` |

The complete external manifest is `$HOME/.cache/stackmate/base-model-manifest.full.json`;
the sanitized relative manifest is
[`base-model-manifest.json`](../../research/model-alignment/reports/base-model-manifest.json).

## Verification

- `BASE_MODEL_PATH` and the repository-specific `STACKMATE_BASE_MODEL_DIR` were set
  only in the active WSL experiment environment.
- The config, tokenizer, and chat template loaded locally with
  `local_files_only=true` and `trust_remote_code=false`.
- The model loaded locally with the same flags and NF4 configuration.
- Offline variables `HF_HUB_OFFLINE=1` and `TRANSFORMERS_OFFLINE=1` were used for
  preflight, training, validation, and locked evaluation.
- The ordinary third-party GGUF remained untouched and was never used as a
  training checkpoint.
