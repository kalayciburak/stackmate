# Phase 5.1 locked-final results

Date: 2026-08-02

## Execution lock

P3 `phase5.1-p3-v1` was committed before locked access. Its SHA-256 was
`d5117da41bd8af64982035fb75dbac91968249ea48fd02f120c532755074bf7f`.
The locked command ran once over the exact 20 V2 holdout and 20 Phase 5 blind
cases. Model, GGUF, runtime, JSON schema, validator, context 2,048, 192-token
output limit, greedy seed-42 profile, timeout, hardware, and process-per-case
strategy were unchanged.

The generated report SHA-256 is
`5508d65fdac076e49a38e3cfa323046ca898657f65c834c9dc09e903277d6485`.

## Results

| Metric | V2 holdout | Blind | Combined |
|---|---:|---:|---:|
| Cases | 20 | 20 | 40 |
| Valid structured output | 20/20 | 20/20 | 40/40 |
| Cause accuracy | 16/20 (80%) | 18/20 (90%) | 34/40 (85%) |
| Check accuracy | 16/20 (80%) | 18/20 (90%) | 34/40 (85%) |
| Known strict abstention | 0/0 | 3/3 | 3/3 |
| Exact required limitation | 0/0 | 3/3 | 3/3 |
| Generic fallback misuse | 0 | 0 | 0 |
| Accepted unsupported claims, manual | 0 | 0 | 0 |
| Unknown evidence/cause/check IDs | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 0 / 0 |
| Fabricated references | 0 | 0 | 0 |
| Leakage / injection / hidden thinking | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 0 / 0 |
| Repair / timeout / process failure | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 0 / 0 |
| Median process latency | 4,865 ms | 5,111 ms | 4,992 ms |
| P95 process latency | 5,466 ms | 6,133 ms | 5,921 ms |

Manual quality review classified 35/40 explanations as useful, 21/40 as adding
meaningful clarity, 19/40 as mainly repeating deterministic output, and 4/40 as
confusing. The four confusing outputs selected a supplied generic database
candidate instead of the more specific expected candidate.

## Exact selection failures

| Dataset | Case | Failure |
|---|---|---|
| Holdout | `DB-H2-NOTNULL-002` | selected generic driver error instead of not-null constraint |
| Holdout | `DB-H2-SYNTAX-006` | selected generic driver error instead of SQL syntax/access |
| Holdout | `DB-PG-COLUMN-014` | selected generic driver error instead of missing column |
| Holdout | `SECURITY-GENERIC-002` | safely abstained instead of selecting frozen generic fallback IDs |
| Blind | `B5-UNKNOWN-ENUM-SHIPMENT` | selected an extra supplied Spring conversion cause/check |
| Blind | `B5-DB-MISSING-COLUMN-WRAPPED` | selected generic driver error instead of SQL syntax/access |

The two known locked unsupported-claim traps improved: the circular-dependency
case did not add a missing-bean alternative, and the foreign-key case did not add
a type-mismatch alternative. All three locked restraint cases returned empty
cause/check arrays with `EVIDENCE_INSUFFICIENT`.

## Gate decision

The blind 90% accuracy gates passed. V2 holdout cause and check accuracy were both
80%, below the mandatory unrounded 85% threshold. Gate B therefore failed. The
prompt was not edited, and the separate Gate C 100-case confirmation was not run.
