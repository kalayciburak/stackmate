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

Two reviews completed in semantic-ID lexical order and independently in
SHA-256 semantic-ID order. Structural atomicity, predicate references, links,
text uniqueness, hedging and ID dependence passed with zero disputes.

~~~text
Ontology SHA-256 4ee991459cab75124b65679ff421ddec3ac3c40d55dd5c7de62d971ad8c3d3d9
Review SHA-256   34d8644a6863210c6caf0988e9d7998ef9a1569192bc5ca4ead2eb371f10b8fe
~~~

Ontology structure is reviewed; model-dataset eligibility remains pending until
Gate C proves dual-label family coverage for all 60 semantics.
