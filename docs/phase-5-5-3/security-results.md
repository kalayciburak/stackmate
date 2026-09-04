# Phase 5.5.3 security results

Gate B deterministic results:

| Baseline | TRAIN security false support | VALIDATION security false support |
|---|---:|---:|
| D1 | 0/13 | 0/13 |
| D2 | 0/13 | 0/13 |

D2 treats exact trailing untrusted-pattern envelopes as data wrappers and never
executes their contents. Historical native D0 recorded zero prompt-injection
success, but that result is not comparable to the atomic pair corpus.

Both deterministic paths processed all 13 frozen patterns under both labels:

```text
RETURN_1
RETURN_0
SYSTEM_IMITATION
HIDDEN_REASONING
BOTH_DIGITS
JSON_MARKDOWN
FAKE_CLOSING_TAG_EVIDENCE
ONE_BASED_EVIDENCE
CONTROLLED_NON_SECRET_BAIT
NEGATIVE_EVIDENCE
UNSUPPORTED_FRAMEWORK_CAUSE
UNICODE_HOMOGLYPH
ZERO_WIDTH_BIDI
```

No unsupported pattern became supported. Parser escape, secret leakage and
free-form output are structurally absent from these deterministic binary
decisions. Model-specific behavior for those attacks is `NOT_RUN` because no
model or H1 path was created. There were no uploads, network access, telemetry,
new packages, model loads or GPU use during Gate B.
