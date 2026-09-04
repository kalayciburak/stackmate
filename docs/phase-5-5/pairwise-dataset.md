# Phase 5.5 pairwise dataset audit

Gate B audited the frozen Phase 5.3 real-world TRAIN and VALIDATION sources
without changing either file. Variant 0 was used once per family so the two
candidate-order variants could not duplicate semantic training units.

The conservative label policy was:

- a reviewed listwise-selected stable ID is `SUPPORTED`;
- an unselected native candidate is `UNKNOWN` unless the source contains
  explicit candidate-local counterevidence or negative polarity;
- only that explicit negative annotation may produce `CONTRADICTED`.

The source inventory produced these potential native pair units:

| Split | Families | Cause pairs | Check pairs | Total | Supported | Contradicted | Unknown |
|---|---:|---:|---:|---:|---:|---:|---:|
| TRAIN | 70 | 103 | 103 | 206 | 102 (49.51%) | 0 (0%) | 104 (50.49%) |
| VALIDATION | 20 | 27 | 27 | 54 | 18 (33.33%) | 0 (0%) | 36 (66.67%) |
| Combined | 90 | 130 | 130 | 260 | 120 (46.15%) | 0 (0%) | 140 (53.85%) |

Every unselected candidate still had resolved evidence references: 104 TRAIN
pairs across 50 families and 36 VALIDATION pairs across 17 families. Those
references establish that a fact was attached to the candidate; they do not say
that the incident rules the candidate out. The native candidate schema has
`id`, `text`, and `evidenceIds`, but no negative-evidence, polarity, required-value,
mutual-exclusion, or cause-to-check-entailment field.

The requested class targets were 30–40% `SUPPORTED`, 20–30% `CONTRADICTED`, and
30–40% `UNKNOWN`. Neither split is a valid three-class dataset, and duplicating
rows or relabeling omission as contradiction would not cure the semantic defect.
Cross-family negative sampling was also rejected because it would transplant an
unreviewed candidate into a different incident and manufacture new ground truth.

Accordingly, `datasets/pairwise-entailment-v1/` was deliberately not created.
The source audit is machine-readable in
`reports/phase-5-5-pairwise-source-audit.json`; its SHA-256 is
`cb9f4d734542c361ff76c7eaa90b261d308fe8aff093f0a9150cc7bb1fda413c`.
Gate B result: `FAIL_CONTRADICTED_CLASS_ABSENT`.
