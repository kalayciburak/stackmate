# Phase 5.5.2 label and dataset tokenization audit

Phase 5.5.2 reuses the frozen binary labels:

~~~text
0 -> token 15 -> NOT_SUPPORTED
1 -> token 16 -> SUPPORTED
~~~

The official tokenizer loaded offline with local_files_only=true and
trust_remote_code=false. Model weights were not loaded. Every row preserved the
assistant prefix and exact suffix:

~~~text
label token, EOS 248046, newline 198
~~~

Only the label token contributes to target loss; both termination tokens are
masked. Results:

| Split | Rows | Prompt average | P95 | Prompt max | Full max |
|---|---:|---:|---:|---:|---:|
| TRAIN | 426 | 298.98 | 347 | 486 | 489 |
| VALIDATION | 186 | 294.09 | 333 | 364 | 367 |

Unknown tokens, truncations and duplicated special tokens were all zero. The
largest full sequence is 489 tokens; 538 tokens are required for a 10% margin,
so the frozen conventional maximum sequence is 1024.

Tokenization audit SHA-256:
b500a6125890add28c15ddee0246447d40b5208d65de5af8a41f52b8600e23d7.
