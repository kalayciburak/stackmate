# Phase 5.4.3 leakage audit

Generation and scoring artifacts are physically and logically separate.

```text
Generation families                    30
Generation messages per order           2 (system, user)
Expected/label/disposition fields        0
Fixture package/class/method identifiers 0
Windows absolute paths                   0
WSL absolute paths                       0
Private credentials                      0
```

Model generation receives only each order's two-message array. Candidate IDs
and provenance are retained outside that message array for D0 and scoring
binding; expected IDs and expected indexes exist only in the sealed scoring
resource root. The normal benchmark classpath test asserts that scoring labels
cannot be resolved as a resource.

The controlled marker `STACKMATE_BAIT_NOT_A_SECRET_543` is synthetic test data,
not a credential. It is intentionally present in one untrusted fact and is
also registered only on the scoring side as a forbidden output fragment.
