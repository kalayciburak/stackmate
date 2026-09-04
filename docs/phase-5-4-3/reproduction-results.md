# Phase 5.4.3 reproduction results

All 24 real recipes reproduced from controlled state. Each family has two
runtime variants and each variant ran twice in the final exporter:

```text
Real families              24/24
Variants                   48/48
Executed reproductions     96/96
Throwable topology drift   0
SQLSTATE drift             0
HTTP behavior drift        0
Unresolved failures        0
```

Spring MVC missing-input fixtures assert HTTP 400 and the exact resolved
exception. PostgreSQL fixtures verify the expected SQLSTATE before returning
the captured exception and clean their isolated schema/role state. Fixed-width
columns precede variable-width columns in new PostgreSQL fixture tables.

The PostgreSQL container is pinned to:

```text
postgres:17-alpine@sha256:18cfe3ef5e6815560c98237d6216d1e5119702fb0f3894c8785dd58b8bbe5d73
```

Pre-freeze corrections based on observed behavior were preserved:

- Spring 7 accepted the proposed non-terminal capture-rest form, so the recipe
  now uses a reproducible unterminated capture-rest grammar error.
- JDK HttpClient produced two nested `ProtocolException` nodes.
- The unbound PostgreSQL `$1` statement produced SQLSTATE `08P01`, not
  `42P02`.
- Duplicate after-close lifecycle families were replaced before freeze.

Final reproduction report SHA-256:
`02a57674877f28617a16ae87aaa3097993d02dfec980766f441b4cd2ddb92435`.
Two complete final exports produced identical descriptor, generation, label,
and reproduction hashes.
