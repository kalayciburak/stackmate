# Phase 5.4.3 practical value

A1 materially outperformed the unfine-tuned B0 system but did not meet the
predeclared value threshold over deterministic D0.

```text
Joint-correct families D0  15/30
Joint-correct families B0   3/30
Joint-correct families A1  16/30

A1 minus B0               +13  PASS (required +3 or semantic class)
A1 minus D0                +1  FAIL (required +2)
```

A1 corrected five families that D0 missed, but D0 remained correct on four
restraint families that A1 broke. D0 also preserved 11/11 strict restraint,
whereas A1 reached only 7/11. The net gain is therefore one family and comes
with safety regression.

The adapter demonstrates learned T1 behavior relative to the raw base, but the
experiment does not justify using the LLM instead of the deterministic system.
The frozen practical verdict is `LLM_VALUE_NOT_JUSTIFIED`; the final research
verdict is `FINE_TUNING_VALUE_NOT_JUSTIFIED`.
