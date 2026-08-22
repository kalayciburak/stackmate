# Phase 5.3 security results

Date: 2026-08-22

## Result

Phase 5.3 adds no production inference surface. Model/data acquisition, training,
remote inference, uploads, telemetry, persistent workers, provisioning, merge, and
GGUF export were not performed.

| Control | Result |
|---|---|
| New corpus source | Controlled local runtime/integration and synthetic fixtures only |
| Production/private incidents | none |
| Private paths or secrets in frozen corpus | 0 |
| Cross-split or prior-corpus prohibited leakage | 0 |
| Final-family execution | 0 |
| F1 diagnostic network / upload / telemetry | none / none / disabled |
| F1 local load | exact revision, `local_files_only=true`, `trust_remote_code=false` |
| Unknown T1 indexes | 0 |
| Hidden thinking | 0 |
| Accepted model prose | none; T1 valid outputs contain selection fields only |

Invalid F1 outputs were retained as diagnostic evidence and never mapped into a
product response. Two outputs attempted old prose/schema fields, 22 omitted required
T1 fields, and one repeated a JSON key; all were rejected without repair.

## New research trust boundaries

- Testcontainers executes a pinned local PostgreSQL image only in explicit labs.
- The Docker daemon is a privileged local test dependency; it is not used by the
  normal build after the explicit labs and no container remains running.
- Generated JSON is scanned after parsing, so serialized escape sequences cannot
  mask or falsely create absolute-path findings.
- Dataset outputs use `CREATE_NEW`, refuse overwrite, and never render final cases.
- Rejected/pre-review datasets and reports remain in ignored WSL cache storage.
- The F1 runner monkey-patches DNS/socket connection entry points after setting
  Hugging Face/Transformers offline and telemetry-disable variables.

Residual risks are controlled-lab realism, repeated same-family prompts, native
CUDA/bitsandbytes execution with WSL-user permissions, Docker/Testcontainers supply
chain, and future adapter overfit. They do not create a current runtime dependency.

The final 11-module Java build passed with Docker stopped. Test-only PostgreSQL,
Testcontainers, and Spring Security dependencies did not enter a runtime module.
