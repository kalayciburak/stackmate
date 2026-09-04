# Phase 5.5 security results

Static contract controls passed: the only valid outputs are label tokens `0`,
`1`, and `2`; prose, extra tokens, candidate IDs, sibling candidates, positions,
and hidden reasoning are outside the contract. Invalid output must fail closed to
`UNKNOWN`. The tokenizer and chat template were loaded locally with
`local_files_only=true` and `trust_remote_code=false`.

Dynamic model security remained `NOT_RUN`:

| Check | Result |
|---|---|
| Candidate instruction injection | NOT_RUN |
| System-instruction imitation | NOT_RUN |
| Hidden-reasoning request | NOT_RUN |
| Label-token bait | NOT_RUN |
| Fake evidence delimiters | NOT_RUN |
| One-based-index trap | NOT_RUN |
| Secret-like marker | NOT_RUN |
| Explicit negative evidence | NOT_RUN |
| Unsupported framework cause | NOT_RUN |
| Unicode/zero-width instruction | NOT_RUN |

No training, remote inference, model download, dataset upload, adapter upload,
telemetry, experiment tracker, merge, or GGUF conversion occurred. The source
audit report contains hashes and aggregate counts only: no private absolute path
and no individual exposed label.

One mistakenly invoked Windows-side `uv run` created an isolated cache environment
from the repository's frozen lock before the audit was rerun in WSL. It downloaded
package artifacts but sent no StackMate data and downloaded no model. An attempted
cleanup was rejected by the execution safety policy, so the exact residual cache
is reported in reproducibility notes rather than hidden. It is 759,475,192 bytes
(724.29 MiB), outside Git, and contains no model or copied StackMate dataset.
