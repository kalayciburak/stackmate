# Phase 5.5.2 atomic contrastive dataset

The reviewed dataset contains one candidate per input. Predicate truth over
trusted evidence determines labels; listwise omission and model output do not.

| Metric | TRAIN | VALIDATION |
|---|---:|---:|
| Families | 60 | 25 |
| Pairs | 426 | 186 |
| SUPPORTED | 189 (44.37%) | 93 (50.00%) |
| NOT_SUPPORTED | 237 (55.63%) | 93 (50.00%) |
| Cause pairs | 110 | 44 |
| Check pairs | 316 | 142 |
| Support bundles | 44 | 20 |
| Hard-negative bundles | 56 | 20 |
| Security rows | 26 | 26 |

Every support bundle contains one cause and its three independently atomic
checks. Every negative bundle uses the same candidate bundle in a context where
its complete predicate is absent. TRAIN has 28 alternate-ontology-signal and 28
curated near-signal negative bundles; VALIDATION has 15 and 5 respectively.
All negative rows are hard or hard-security; easy negatives are zero.

All 60 candidate semantics have SUPPORTED and NOT_SUPPORTED contexts in both
splits and at least two distinct family contexts per label overall. Canonical
candidate text dual-label coverage is 100% in each split. Thirteen security
patterns occur once per label in each split.

Each of 612 pairs received two deterministic predicate-recomputation checks.
These are self-consistency passes, not independent semantic reviewers. Separate
semantic review is recorded in pair-review.md.

~~~text
Dataset manifest SHA-256 3a48c4e4721b5b7cb590ff16f7b95ef1afaa557a439f12dc6002065df260f67a
TRAIN SHA-256            cb5f1ef1611b4f4383226dcc0fb10729cb2bac1aafacfb374cc29141f1769313
VALIDATION SHA-256       d6496481d7073a76605c744f11dd03e0d8a3fcc79ff9e8418bba187391373701
Families SHA-256         49a1fca8c42781be3d472f45edb8ef7a195b815a7c855bbbfdff0c66dd1816a4
Pair reviews SHA-256     5d6d94a3cf62b962c2a54f39e9a6f5a73c12dd13452b77f9270cf8c77ae28622
~~~
