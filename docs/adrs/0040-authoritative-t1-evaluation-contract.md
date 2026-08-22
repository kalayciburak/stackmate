# ADR 0040: Authoritative ordinal T1 evaluation contract

Status: Accepted for Phase 5.4.2 recovery

The Phase 5.4.1 adapter is frozen to the ordinal T1 contract. T1 scoring must
be independent from the historical schema-v2 ID/prose contract and must reject
schema-v2 fields rather than silently translating them.

The canonical contract is implemented in:

```text
research/model-alignment/scripts/t1_contract.py
research/model-alignment/scripts/T1Contract.java
```

Both implementations are checked against 50 golden fixtures. Fresh-final
execution remains blocked until an independent corpus exists.
