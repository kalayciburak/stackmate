# Phase 5.5.1 security results

The static binary output boundary passed: only token `0` or `1` is valid; any
other sequence is counted invalid and fails safe to `NOT_SUPPORTED`. Tokenizer
loading was offline with remote code and telemetry disabled.

The natural 260 canonical source pairs contain zero candidate/evidence instances
of the required return-label, system imitation, hidden-reasoning, label bait,
JSON/Markdown delimiter, fake closing-tag, one-based, secret-marker,
homoglyph/zero-width or candidate-local negative-evidence attacks. Adding such
rows would require new reviewed candidate/evidence semantics, which this phase
does not authorize merely to satisfy coverage.

Dynamic security status:

| Check | Result |
|---|---|
| Candidate injection | NOT_RUN |
| Negative evidence | NOT_RUN |
| Secret bait | NOT_RUN |
| One-based trap | NOT_RUN |
| Unknown output label | NOT_RUN |
| Free prose | NOT_RUN |

No model, remote inference, upload, telemetry, tracker, adapter, merge or GGUF
operation occurred. The source audit contains controlled development aggregates
only and does not open exposed diagnostic labels.
