# Phase 5.4.2 reproducibility

Completed without model execution:

```text
uv lock --check
uv run --frozen python -m py_compile scripts/*.py tests/*.py
uv run --frozen python -m unittest discover -s tests -v
```

The Python suite passed 12/12. The authoritative Python and Java T1
implementations agreed on all 50 golden fixtures. The original evidence hashes
and forensic output hash are recorded in the Phase 5.4.2 reports.

```text
Original inventory: 78f2cebdbe80e949e98258b9d3618f023002c3dbdaa70d4f1a75088644fa4fa8
Contract parity: e79c0672919236102ce41f54bb9f73da271ef970d9c86b9dbda072c9af2ceb80
Forensic rescore: d87eb04f4a9ee8235453f66048a44178bcfb48b5e28f59c49c7252b5fa2eaa90
T1 cross-language parity: 6dbfd9271a9bbea33731e42a0bbe4a69370968118a091f83f7b0132d05e8e2c4
Fresh-final availability: 795bb0e7436bb7b5d5f8480f89fdad46a442165f7ebd2216ec608f57c41c5dfe
```

No fresh-final generation, merge, GGUF conversion, or remote push was run.

The normal Java verification also passed with 516 Surefire tests and 11
Failsafe tests, all with zero failures/errors/skips.
