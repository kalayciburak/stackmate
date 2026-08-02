# Phase 5.2 leakage audit

Date: 2026-08-02

## Decision

`PASS`: prohibited overlap count is zero. The dataset may enter bounded training.

The audit compared 1,000 generated examples with the 20 Benchmark V2 holdout
cases, 20 Phase 5 blind cases, Phase 5.1 locked-final split, all 30 frozen
post-training challenge cases, and six Java runtime/incident fixture sources.

| Check | Result |
|---|---|
| Exact normalized fact/candidate text | 0 matches |
| Locked case or recipe identifiers | 0 matches |
| Template-family overlap | 0 matches |
| Train/validation family overlap | 0 families |
| Character 5-gram threshold | 0 at or above `0.88` |
| Highest observed 5-gram similarity | `0.243590` |
| Exception-category overlap | 15 categories, reported and allowed |
| Evidence-signature overlap | 0 |
| Candidate-selection signature overlap | diagnose and abstain shapes, reported and allowed |

Category and selection-shape overlap is expected: training must teach the same
product task without copying test messages. Neither is considered leakage by
itself. Rejection requires exact normalized content, a locked identifier or
template, a train/validation family collision, or field-level character 5-gram
similarity at or above the frozen threshold.

## Frozen sources

| Source | SHA-256 |
|---|---|
| Benchmark V2 manifest | `292b395f6024837a52878b38a6c0170a62cb29fdfcc89df68b845ced7732f294` |
| Blind manifest | `eeab25e72c2a63785d45381681bdfb9f850c2e17e721c79750b4d576e6aba52a` |
| Phase 5.1 split manifest | `147d9a9e7e312d02abbf113ed3fd255bd4a3ee7841413c488f36deb44c016a07` |
| Post-training challenge | `9b5477bf2e1ea1593b3559a2696a0f5e9043f8d34577fbc86be85663d3d6f5e1` |

The challenge checksum is enforced against its lock before comparison. The audit
report contains aggregate results and hashes, not copied challenge messages.

## Limitations

Character n-grams detect lexical reuse, not every semantic paraphrase. The
independently named family split, controlled template authorship, absence of
locked inputs from the generator, identifier scan, and later untouched challenge
evaluation are therefore all required controls; this audit is not a claim that a
single similarity metric proves semantic independence.
