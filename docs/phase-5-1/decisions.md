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

## D7: P3 wins Gate A under the frozen priority order

P3 is selected because it has zero manually accepted unsupported claims, 8/8
strict abstention, zero generic-fallback misuse, 60/60 validity, and 54/60 exact
cause/check selection across development and selection validation. P0 fails the
primary safety criterion; P1 misses one abstention and one valid output; P2 falls
below 90% exact selection. No prompt was edited after either result was observed.
