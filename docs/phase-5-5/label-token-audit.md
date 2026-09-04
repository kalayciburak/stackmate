# Phase 5.5 label-token audit

The exact official tokenizer was loaded offline with
`local_files_only=true` and `trust_remote_code=false`. No model weights were
executed.

```text
0 -> token 15 -> CONTRADICTED
1 -> token 16 -> SUPPORTED
2 -> token 17 -> UNKNOWN
```

Each plain digit is exactly one token and decodes to itself. Leading space and
newline forms are deliberately different two-token sequences, so the contract
does not permit leading whitespace. In three normal/adversarial chat contexts,
the assistant suffix was consistently:

```text
label token, EOS 248046, newline 198
```

The label token was always the first token after the official assistant
generation prefix. Prompt lengths for the audit contexts were 221–228 tokens.

```text
Prompt SHA-256  93515b517ff08c5bcf92764936e62a4c8709652fe0ff14152fbe0f6fc3a96517
Mapping SHA-256 98b223427fc6be46cedbe74fd3e10c9696900fba59f032387d63288925e2de76
Report SHA-256  a40ed23a899abd97c91f4aba5888118291bfd352c3c5ea538de68dc19dc291c4
```

An invalid token, additional token, JSON, punctuation, prose, Markdown, or
hidden reasoning must fail closed to `UNKNOWN`; no output repair is permitted.
