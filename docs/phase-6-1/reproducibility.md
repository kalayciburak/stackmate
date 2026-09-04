# Phase 6.1 reproducibility qualification

## Current local evidence

Two clean Windows public-release builds produced 23 byte-identical compared
payload files with zero differences. Both clean-build bundle manifests were
`bb35cc3becdd7bfdbcbda43756f652fc8f8809b73c9a332ea68259dd4d71e408`.
The comparison report SHA-256 is
`52ba8f8ddeb3cfaaa0a40aec9ce768c634dbc4be19cb4db54cd8c55a3e23712e`.

After recording that result in the bundle, the 26-file local rc2 manifest is
`61a806934c50c8c89eb0d4b5f5f8eeab816ac9ce0a304c55b9d3adbb89eb3a1f`.

## Remote design, not remote evidence

The locally implemented Ubuntu job performs two clean public builds, assembles
both bundles and compares their release payloads. It retains the comparison
report and both manifests for seven days. No remote run exists, so its result is
`UNVERIFIED`.

| Evidence | First build | Second build | Comparison/result |
|---|---|---|---|
| Local clean builds | `bb35cc3b...d71e408` | `bb35cc3b...d71e408` | `PASS`, 23/23 identical |
| Ubuntu remote clean builds | `PENDING` | `PENDING` | `UNVERIFIED` |

## Main update after completion

Main must record:

- final rc2 commit SHA and build environment;
- first and second local manifest hashes plus comparison conclusion;
- final local bundle checksum;
- Ubuntu run/job URL, first and second manifest hashes and comparison result;
- uploaded artifact name, checksum and seven-day expiry; and
- any differing payload path with an explicit disposition.

Reproducibility evidence is qualification only. It does not sign, publish,
deploy, tag, merge or create a release.
