# Phase 7 public visibility readiness

## Current decision

`KEEP_PRIVATE / EXPLICIT_CONSENT_REQUIRED`

Gate C requires the current public-facing documentation to contain no actual
machine-local paths. The working-tree documentation uses semantic placeholders:

- `<workspace>` for a repository checkout root when one is needed;
- `<jdk-21-home>` for the local Java 21 installation;
- `<external-training-cache>/<phase-subdir>` for external model-alignment caches.

Historical metrics, model revisions, evaluation outcomes, and research semantics
are unchanged. Frozen research datasets, reports, scripts, and tests are not
modified.

## Readiness boundaries

| Check | Status |
| --- | --- |
| RC3 source delta Gitleaks findings | Pass: 0 |
| New RC3 machine-local paths | Pass: 0 |
| Current public-facing docs actual machine-local paths | Pass: 0 across 340 scanned files; one explicit placeholder example retained |
| Historical machine-local paths | Retained and recoverable |
| Git author, committer, tagger, and project contact identity | Retained |
| History rewrite | Not performed |
| Public visibility authorization | Not granted |

The path-only documentation sanitization is outside the immutable RC3 source
commit or tag. It changes no runtime code, dependency graph, artifact content,
version, metric, or qualification result and therefore does not qualify a new
release candidate. It still requires its own normal source-control review and
commit before it can affect a published repository view.

## Required later consent

No visibility setting may be changed implicitly. A later authorization must
explicitly acknowledge that making the existing repository public also exposes
the preserved historical path values and public author identity metadata. Until
that informed consent is recorded, the safe action is to keep the repository
private.

If historical path disclosure is not accepted, publication requires a separately
authorized sanitized-publication strategy. This audit does not authorize a
history rewrite, a new public repository, a force push, or any remote visibility
change.
