# Phase 5.4.2 decisions

- Preserve the Phase 5.4.1 adapter, checkpoint, prompt, base revision, and
  generation settings.
- Treat the original schema-v2 locked result as contract-contaminated, not as a
  semantic T1 qualification.
- Use the new authoritative ordinal T1 parser for forensic and future scoring.
- Do not reinterpret schema-v2-like ID outputs as T1 indexes.
- Do not train, tune, change the adapter, or use original final results for any
  adjustment.
- Do not create a fresh final set from renamed or recycled families.
- Stop at Gate D if 24 independently reproducible real families and six
  independent synthetic/adversarial families cannot be created honestly.
- Do not push during this recovery phase, even though earlier phases were
  pushed to the private repository.
