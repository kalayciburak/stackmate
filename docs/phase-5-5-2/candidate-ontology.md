# Phase 5.5.2 atomic candidate ontology

The first ontology contains 15 atomic cause propositions and 45 linked atomic
check actions. Semantic IDs remain metadata only; the model receives canonical
text and trusted evidence.

| Area | Cause predicates |
|---|---:|
| SQLState class 22 / class 23 | 2 |
| Database lock / cancellation | 2 |
| Java runtime | 4 |
| HTTP / TLS | 2 |
| Spring configuration | 2 |
| Authentication | 1 |
| Jackson / web request | 2 |
| Total | 15 |

Every cause has exactly three independently selectable checks. Each canonical
text is ASCII NFC, ends with one period, contains one proposition or action, and
contains no comma, semicolon, slash, standalone and/or, hedging, probe text,
candidate ID, evidence ID or position.

Direct support uses only reviewed predicates:

~~~text
SQL_STATE prefix 22
SQL_STATE prefix 23
SQL_STATE exact 55P03
SQL_STATE exact 57014
EXCEPTION_CLASS exact or approved exact-any value
~~~

Checks inherit their linked cause predicate. NOT_SUPPORTED requires that the
complete predicate be absent or disqualified; listwise omission is never enough.
No candidate is deprecated and no unsupported mutual-exclusion relation is
invented.

The generated lexical and SHA-256-order passes verify deterministic structural
consistency; they are not described as independent semantic reviewers. A
separate experiment-owner review and separate-context adversarial review found
five initial defects, corrected them, then recorded zero unresolved semantic
issues against the final hashes.

~~~text
Ontology SHA-256          f24f0128fbcba5754888f1abd7484f4b5530f2219da431d60923cafe16a1e793
Structural review SHA-256 eaf35e665c794a44dce137ca2a44dcee6c3f24c44cd6752276062bcda2e831c8
Independent review SHA-256 b83b511bd0b3e9448a68e87ba76e6bc3f4724f0271daef54fd4a4ed2e724d47f
~~~

Ontology structure is reviewed; model-dataset eligibility remains pending until
Gate C proves dual-label family coverage for all 60 semantics.
