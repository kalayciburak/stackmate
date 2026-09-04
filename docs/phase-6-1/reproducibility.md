# Phase 6.1 reproducibility qualification

## Current local evidence

Two clean Windows public-release builds produced 23 byte-identical compared
payload files with zero differences. Both clean-build bundle manifests were
`bb35cc3becdd7bfdbcbda43756f652fc8f8809b73c9a332ea68259dd4d71e408`.
The comparison report SHA-256 is
`52ba8f8ddeb3cfaaa0a40aec9ce768c634dbc4be19cb4db54cd8c55a3e23712e`.

After recording that result in the bundle, the 26-file local rc2 manifest is
`61a806934c50c8c89eb0d4b5f5f8eeab816ac9ce0a304c55b9d3adbb89eb3a1f`.

## Remote evidence

Ubuntu job `101197201404` in run
[33926362199](https://github.com/kalayciburak/stackmate/actions/runs/33926362199)
performed two clean public builds and passed 23/23 payload comparisons with
zero differences. Both manifest SHA-256 values were
`b283738fd53d886c5b3677366c1d8928de3e4e2ea006aee0e3f5b0e7c8c1ce53`;
downloaded comparison JSON SHA-256 was
`cd4df30d714706106a4515b8ba6b20412828d53f6962e58bef75916b4c76a265`.
GitHub artifact digest was
`16c938b4cf3bf50b69998a6bd78eef4b7aadef65b5752d5636f2e69f53a93132`
with expiry `2026-09-11T22:47:37Z`.

| Evidence | First build | Second build | Comparison/result |
|---|---|---|---|
| Local clean builds | `bb35cc3b...d71e408` | `bb35cc3b...d71e408` | `PASS`, 23/23 identical |
| Ubuntu remote clean builds | `b283738f...c8c1ce53` | `b283738f...c8c1ce53` | `PASS`, 23/23 identical on exact checkout; summary identity rerun pending |

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
