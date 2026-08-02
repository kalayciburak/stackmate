# Phase 5 console evidence

Phase 5 added no model-backed product console path because Gate A failed. Actual sanitized deterministic sample
output remains under [`baseline/`](baseline/). It proves immediate diagnostics, unknown-error restraint, redaction,
duplicate handling, disabled mode, and unchanged HTTP 500 behavior.

The qualification lab produced validated research JSON, not a supported `StackMate Local Explanation` console
block. Example accepted research output for a unique violation:

```json
{
  "summary": "Database unique constraint violation detected during execution.",
  "explanation": "The application encountered a SQLState 23505 indicating a unique constraint violation while executing a blind scenario service.",
  "selectedCauseIds": ["cause.database.unique-constraint"],
  "selectedCheckIds": ["check.database.unique-constraint"]
}
```

An unsafe research example was accepted by automatic validation for a real foreign-key case:

```text
The SQLState 23506 indicates an integrity constraint was violated,
likely due to a data type mismatch or invalid value.
```

That unsupported alternative is quarantined as qualification evidence and is one reason no local-explanation
console integration was implemented.
