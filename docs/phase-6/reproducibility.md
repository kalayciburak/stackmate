# Phase 6 reproducibility

## Provenance

```text
Branch                  phase-6-public-alpha-hardening
Phase start             629f24001cc7fbcb5f36e8df2fe4f96c1ef39d87
Baseline checkpoint     c14cbfe270f8520d379712209afd46fc8e4d5f1d
Version                 0.1.0-rc.1
Publication             not performed
Signing                 not performed
```

The accepted pre-hardening baseline passed 517 Surefire and 11 Failsafe tests.
The RC public-release clean install, external consumer, API regeneration and OSV
scan passed. A full-reactor attempt exposed the stale removed
`stackmate.console.ansi` property in the sample E2E configuration; after that
configuration and a benchmark fixture contract were corrected, a clean full
reactor passed 543 Surefire and 5 Failsafe tests with zero failures, errors, or
skips.

## Reproducible release procedure

Use the commands in `verification-commands.md` from a clean checkout with JDK
21. After the final full verify, assemble the local RC twice into different
empty output roots and compare both bundles with
`release/compare-release-bundles.ps1`.

The comparison intentionally excludes generated checksum manifests and the
release audit report. A `PASS` therefore means all compared release payload
files are byte-identical; it does not assert that timestamps or excluded report
files match.

## Final evidence to record

```text
Final commit                  commit referenced by local annotated tag v0.1.0-rc.1
Full verify totals            PASS, Surefire 543 / Failsafe 5 / failures-errors-skips 0-0-0
Public API type count/hash    73 / 41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7
Final OSV result              PASS, 0 known vulnerability groups
JMH raw report SHA-256        ba13b7865e67ed80cfe4896018c50ec7edb2f471045eb0b91e754e659c9bae6a
Heap probe report SHA-256     cf9eab70a0cc53ed531bbc5e60e0a8458fc2d8074d7edd2170915632fe9ceda1
First clean-build manifest    bb708acdd3bcd100f60695c6e1a7500e84094095c91550bd8bac39bfb352f256
Second clean-build manifest   bb708acdd3bcd100f60695c6e1a7500e84094095c91550bd8bac39bfb352f256
Clean-build comparison        PASS, 18 payload files / 0 differences
Comparison report SHA-256     15afd9d8b14ff1e94c9eb69a361f4f0206225a39c18835be0d649d2e104ec3c2
Final payload comparison      PASS, 21 payload files / 0 differences
Final payload report SHA-256  1d73bcb11c5c60d991e8df18c208563f4dd4dabde2f1916293a3c90b27eb7070
Final bundle manifest         108dbbb31f207fa1db68355ae536c63572468293dddfc79ffcc3a0557447b3da
Remote CI URLs/results        UNVERIFIED
```
