# Phase 5.1 decisions

## D1: Phase 5 evidence remains immutable

Phase 5 completion is tagged at
`d29d789933a6394db942435e2c5ef607cda53f2f`. Historical reports are not edited.
Phase 5.1 generated evidence uses new `docs/phase-5-1/` and ignored target paths.

## D2: The primary comparison changes prompt content only

P0-P3 use the same pinned model/GGUF/runtime, request construction, JSON grammar,
schema, semantic validator, corpus, ground truth, context, output limit, seed,
generation settings, timeout, hardware, and process-per-case strategy. Validator
experiments occur only after prompt-only results and are reported separately.

## D3: Manual and automatic unsupported-claim counts are separate

The Phase 5 automatic detector misses the three reproduced unsupported alternatives.
Phase 5.1 will record generated, rejected, accepted, and manually discovered claims
separately. An automatic zero cannot overwrite a manual finding.

## D4: Repair cannot change semantic meaning

Repair remains bounded to one attempt and may address only structural/schema issues.
A semantic-grounding rejection is final and falls back; it is not supplied to a
model for semantic rewriting.

## D5: No product integration follows from prompt experimentation alone

This phase does not implement a persistent worker, production llama.cpp adapter,
model/runtime provisioning, download, packaging, or Spring integration. A passing
prompt can produce only a `READY_CANDIDATE` verdict for the next separately
authorized qualification phase.

## D6: The split is frozen before prompt variants

The first 40 V2 development cases in manifest order form prompt development; the
remaining 20 V2 development cases form selection validation. Existing 20-case V2
holdout and 20-case blind corpora form the locked final. The split manifest digest is
`147d9a9e7e312d02abbf113ed3fd255bd4a3ee7841413c488f36deb44c016a07`.

## D7: P3 was locked under the first-pass Gate A review

P3 was selected with 8/8 strict abstention, zero generic-fallback misuse, 60/60
validity, and 54/60 exact cause/check selection. The first pass incorrectly recorded
zero unsupported claims. A later reverse-order pass found one unsupported `high
rejection rate` claim in the selection set. The lock remains immutable; the error
is reported and P3 is not replaced after Gate B.

## D8: No prompt passes the final Gate A audit

P0, P1, and P3 each contain an accepted unsupported claim. P2 remains at 53/60
exact cause/check selection, below 90%. The controlled study therefore cannot claim
that a reasonable prompt passed all development gates.

## D9: Gate C is skipped after Gate B failure

P3 scored 16/20 cause/check on V2 holdout, below the mandatory 17/20 floor. The
separate 100-case Gate C command is not run. The 100 outputs already observed over
the three disjoint gates may be summarized diagnostically, but are not relabeled as
a full-confirmation run.

## D10: Lexical claim checking is not adopted

A research-only lexical validator catches all three Phase 5 known claims but also
flags seven other Phase 5 outputs. Combined acceptance falls to 83/100, below the
98% validity gate. The production validator adopts only general empty-selection
limitation and contradictory-selection rules; case-independent lexical checking
remains experimental.

## D11: Clause-level grounding is not adopted

On the 20-case selection-validation set it preserved 20/20 validity and 18/20 exact
selection, and all clauses carried supplied evidence IDs. It did not remove the
unsupported `high rejection rate` claim, while median process latency rose from
4,862 ms to 5,101 ms. Evidence-ID attachment improves auditability but does not
prove that the clause is semantically entailed.
