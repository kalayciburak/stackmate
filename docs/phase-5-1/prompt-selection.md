# Phase 5.1 prompt selection

Date: 2026-08-02

## Locked selection

| Field | Value |
|---|---|
| Selected prompt | P3, closed world with four negative examples |
| Prompt version | `phase5.1-p3-v1` |
| Prompt SHA-256 | `d5117da41bd8af64982035fb75dbac91968249ea48fd02f120c532755074bf7f` |
| Prompt characters | 3,259 |
| Estimated tokens | 815 using `ceil(characters / 4)` |
| Gate A evidence commit | `48e59bd3d7c52ad7afe0869a6f76e337ea965285` |
| Selection lock commit | `041a0d0947580272b1226f3224dd279523e8bc83` |

The lock file is
`stackmate-benchmark/src/main/resources/prompt-ablation/selected-prompt.json`.
Locked-final and full-confirmation harness modes refuse to run without an exact
prompt ID, version, and digest match.

## Selection rationale

The frozen priority was safety and restraint before exact-selection quality.
Across the 40-case development and 20-case selection-validation sets, P3 produced:

- zero manually accepted unsupported technical alternatives;
- 8/8 strict abstentions and 8/8 exact `EVIDENCE_INSUFFICIENT` limitations;
- zero generic-fallback misuse;
- 60/60 structurally and semantically accepted outputs;
- 54/60 exact cause selections and 54/60 exact check selections;
- 56/60 useful explanations in the selection-time manual pass; and
- median process latency of 5,340 ms on development and 4,862 ms on selection
  validation.

The six exact-selection misses were not hidden: four development database cases
preferred a supplied generic candidate over a more specific supplied candidate;
two selection-validation security cases abstained instead of choosing supplied
generic fallback IDs.

## Rejected variants

- P0 retained 56/60 exact cause/check selection, but reproduced one accepted
  unsupported driver alternative, passed only 1/8 strict abstentions, and used a
  generic fallback three times. It fails the first selection criteria.
- P1 produced 54/60 exact cause/check selection but only 7/8 strict abstentions
  and 59/60 valid outputs. It loses to P3 before prompt size or latency is
  considered.
- P2 produced 53/60 exact cause/check selection (88.33%), below the 90% quality
  requirement, and had seven confusing development outputs. Its smaller prompt
  does not compensate for that regression.

## Known trade-offs

P3 is 1,989 characters larger than P0. Its development median end-to-end process
latency was 90 ms above P0, while selection-validation median latency was 120 ms
above P0. Prompt-evaluation latency increased, but shorter generation offset most
of that cost. These cold process-per-case measurements do not predict a future
persistent worker.

P3's four negative examples cover the three Phase 5 failure themes and one
unrelated insufficient-evidence category. The examples contain no benchmark case
ID, exact exception message, or exact expected candidate text. Generalization must
be demonstrated on the still-unseen locked holdout and blind sets; the prompt will
not be edited after that result.
