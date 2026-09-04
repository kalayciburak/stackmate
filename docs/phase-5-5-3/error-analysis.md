# Phase 5.5.3 error analysis

Gate B produced no D1 or D2 classification errors on TRAIN or VALIDATION:

```text
D1 false support / missed support  0 / 0
D2 false support / missed support  0 / 0
Gate B taxonomy counts             all 0
```

D0 is `NOT_APPLICABLE` for 612/612 atomic rows because no frozen product-ID to
atomic-semantic crosswalk exists; this is a scope limitation, not 612 model
errors. Model, calibration, order-instability and aggregation failure classes
were not reviewed because Gates D-G were not run.

The zero-error result is construction-bound. It does not establish that the
product engine solves all real-world incidents.
