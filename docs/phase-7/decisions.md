# Phase 7 decisions

1. Fast-forward canonical main to the already qualified closing HEAD; do not rewrite history. Close superseded
   draft PR1 instead of merging it into the obsolete phase base.
2. Protect main from force-push/deletion and require existing CI/CodeQL statuses, with solo-owner admin bypass.
3. Preserve the failed manual preflight [33961572492](https://github.com/kalayciburak/stackmate/actions/runs/33961572492).
   Its `clean install` reached the empty E2E module after all E2E tests passed, then failed because no main artifact
   exists. The correction is `clean verify` for all modules followed by `-Ppublic-release -pl ... -am install` for
   the public chain. Do not generate a fake E2E artifact or broaden the public allowlist.
4. This release-workflow correction requires RC3. RC1/RC2 tags are immutable. Frozen tests and public runtime
   code remain unchanged. Recorded RC2 API/reproducibility files stay historical baselines.
5. External Maven consumers and controlled PostgreSQL stay outside Git. Only synthetic data and loopback network
   fixtures are allowed. Original application HTTP behavior is compared across enabled/disabled/uninstalled runs.
6. No eligible personal project was found without a framework upgrade. Do not upgrade or modify unrelated apps.
7. Automated incidents do not count as organic human dogfood. Stop before final release/publication until the
   user completes the journal and the acceptance gate passes; publication still needs separate authorization.
