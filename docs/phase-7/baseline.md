# Phase 7 baseline

Status: `PASS` for the inherited baseline only. This file does not qualify RC3.

## Revision identity

| Item | Evidence |
| --- | --- |
| Phase 7 baseline | `56dc1f8452da90f558807790c20d7cf1eac0fc00` on `phase-6-public-alpha-hardening` |
| Qualified RC2 source | `357ca1418eb3786e861e4436fe55005c8c6456cd` |
| RC2 annotated-tag object | `1b51e78d40b5dfe1e1b22c9fdb24756eef0e57f1` |
| RC2 publication state | Local and unpushed |

RC2 remains an immutable historical qualification point. Phase 7 does not move or reuse that tag.

## Preserved verification evidence

- Root reactor: 543 Surefire and 5 Failsafe tests.
- Separate external consumer: 6 Surefire and 1 Failsafe tests.
- Combined total: 549 Surefire and 6 Failsafe tests, with zero failures, errors, or skips.
- Frozen public API: 73 types; SHA-256
  `41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7`.
- Local file repository: 13 required payloads and 26 MD5/SHA1 sidecars; digest recalculation passed.

These RC2/baseline checks were repeated locally at Phase 7 start, not merely inferred from history.
The current Phase 7 source and RC3 must obtain their own qualification
evidence before either can be described as qualified.

See [the RC2 identity record](../phase-6-1/rc-identity.md) and
[platform qualification](../phase-6-1/platform-qualification.md) for the bounded historical evidence.
