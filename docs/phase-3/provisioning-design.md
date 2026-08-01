# Phase 3 provisioning decision

Status: **not implemented**.

The proposed Testcontainers-inspired provisioning design was explicitly conditional
on Gate A and Gate B. Gate A failed for both Qwen3.5 candidates, so implementing a
catalog, downloader, cache, native-runtime installer, mirror, Spring properties, or
background startup path would make a failed model easier to install. That is outside
the authorized gate sequence.

Consequently:

- the deterministic starter still performs no runtime network access;
- the local starter still performs no automatic download or update check;
- model files remain user-supplied and ignored by Git;
- there are no `AUTO`, `LITE`, `QUALITY`, or provisioning-policy product settings;
- there is no cache activation, first-run download, cache-hit, mirror, or offline
  import product behavior to demonstrate;
- normal CI remains model-free.

The conceptual requirements in the Phase 3 prompt remain future acceptance criteria,
not implemented behavior. A later phase must begin with a new passing model
hypothesis rather than reusing this failed Gate A decision.

