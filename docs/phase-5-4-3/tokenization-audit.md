# Phase 5.4.3 tokenization audit

The exact official Qwen3.5-2B tokenizer was loaded offline from revision
`15852e8c16360a2fea060d615a32b45270f8a8fc` with
`local_files_only=true` and `trust_remote_code=false`.

| Measurement | Count | Mean | P95 | Min | Max |
|---|---:|---:|---:|---:|---:|
| System + user prompt | 60 | 766.6 | 898 | 484 | 899 |
| Expected T1 target | 60 | 19.1 | 21 | 18 | 21 |
| Prompt + expected target | 60 | 785.7 | 916 | 502 | 920 |
| Prompt + 128-token generation budget | 60 | 894.6 | 1026 | 612 | 1027 |

```text
Truncations            0
Unknown tokens         0
Maximum sequence       2048
Tokenizer              Qwen2Tokenizer
Padding side           right
Pad token ID           248044
EOS token ID           248046
BOS token ID           null
Chat template SHA-256  273d8e0e683b885071fb17e08d71e5f2a5ddfb5309756181681de4f5a1822d80
```

Corpus audit SHA-256:
`a8c9a246f215581b0ca5ab4dc031729764ccfb3797bd511b2c89119caae0acc6`.
