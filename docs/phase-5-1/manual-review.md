# Phase 5.1 manual review

Date: 2026-08-02

## Method and limitation

One reviewer performed both passes. Pass one read development, selection, and then
locked outputs in corpus order. Pass two read the selected P3 outputs in reverse
order with a stricter technical-entailment focus. This lacks reviewer independence;
the disagreement below is retained rather than concealed.

The review covered:

- every P3 output with a limitation or empty selections (14 outputs);
- all 11 predeclared restraint cases;
- all generated or experimentally flagged unsupported claims;
- the only primary validator rejection and bounded repair attempt;
- all six locked exact-selection failures; and
- every remaining P3 output, including more than 20 normal successes.

## Two-pass result

| Classification | Pass one | Pass two / final |
|---|---:|---:|
| Correct/useful | 91/100 | 90/100 |
| Adds meaningful clarity | 46/100 | 45/100 |
| Mainly repeats deterministic output | 54/100 | 55/100 |
| Confusing | 8/100 | 9/100 |
| Accepted unsupported speculation | 0 | 1 |

The disagreement is `CONC-REJECTED-004`. P3 correctly selected executor rejection
but stated that it was due to a `high rejection rate`. The capture establishes a
rejected execution; it does not measure a rate. Pass one treated this as a harmless
paraphrase. Pass two correctly classified it as unsupported and confusing. P1 made
the same addition; P2 did not.

## Limitations and empty selections

All 11 predeclared restraint cases returned empty cause/check arrays and exact
`EVIDENCE_INSUFFICIENT` limitations under P3. Three additional cases safely
over-abstained despite frozen ground truth selecting generic IDs:
`SECURITY-SIGNATURE-003`, `SECURITY-CERT-004`, and `SECURITY-GENERIC-002`.
These are honest limitations, but the three exact-selection misses remain scored.

## Unsupported and experimental-validator review

The research lexical validator flagged three P3 outputs:

| Case | Review |
|---|---|
| `DB-H2-TABLE-004` | false/over-conservative flag; the `SQL syntax or access` phrase is supplied candidate text |
| `CONC-REJECTED-004` | true unsupported claim; `high rejection rate` is not supplied evidence |
| `B5-DB-MISSING-COLUMN-WRAPPED` | wrong generic selection, but its prose stays within the selected supplied candidate |

Thus the selected prompt has one accepted unsupported claim, not the automatic
counter's zero and not the lexical counter's three.

## Rejections and repair

The primary ablation had one invalid response: P1 on `DB-H2-CONVERSION-007`.
The initial JSON/schema failure received exactly one bounded repair attempt; the
repair remained invalid and fallback succeeded. No semantic failure was repaired
into acceptance. P3 had no invalid response or repair attempt.

Post-run replay with the production restraint rules rejected seven Phase 5 P0
outputs that had empty selections without limitations. It did not silently alter
them. The research lexical layer's seven extra Phase 5 rejections were judged too
broad for adoption.

## Manual conclusion

P3's restraint is substantially better than P0, but its single unsupported rate
claim, six locked selection misses, reduced added clarity, and nine confusing
outputs prevent a passing safety/quality conclusion.
