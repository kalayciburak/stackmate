# Phase 7 findings and disposition

| ID | Severity / ownership | Finding | Resolution and evidence |
| --- | --- | --- | --- |
| P7-01 | RELEASE_BLOCKER / release workflow | Manual preflight installed the whole reactor; empty E2E artifact fails install after its tests pass | Split full `clean verify` from public-only `install`; RC3 exact-source hosted preflight33962139468 passes. Original failed33961572492 retained. No runtime/test behavior change. |
| P7-02 | LOW / invocation | External absolute bundle path doubled with repo root in the collector | Pass repository-relative path to the existing script. Original failed invocation retained; no product change. |
| P7-03 | LOW / invocation | Unquoted PowerShell `-Dstackmate.version` split into an invalid Maven lifecycle phase | Quote the complete `-D...` argument. Corrected consumer6+1 and file-repo6+1 pass; failed log retained. |
| P7-04 | LOW / external harness | Startup poll parsed an empty output file before Tomcat logged | Check for non-empty content before regex. Three pre-start harness attempts preserved; only their scoped JVMs were stopped. Later complete runs exited gracefully. |
| P7-05 | LOW / external summary | Uncaptured advice-handled UNKNOWN boundary was mistakenly counted as a false positive | Count an actual captured diagnosis before classifying it as a false positive. Original failed summary retained; raw requests/results unchanged, no model or validator tuning. |
| P7-06 | DOCUMENTATION / external fixture | Service scenario root metadata used Spring/async wrappers instead of deepest causes; compiler parameter metadata missing | Corrected expected PSQLException/IllegalState roots and enabled `parameters` before the first incident run. No product change. |
| P7-07 | OUT_OF_SCOPE | Malformed JSON, validation, type conversion and advice404 are resolved by Spring/application advice | Report4/20 uncaptured boundaries; preserve400/404 semantics. Do not broaden capture scope. |
| P7-08 | DOCUMENTATION / privacy | Historical and current documentation contained machine-local paths | Neutralize current documentation paths; preserve original commit/tag history and record residual visibility privacy decision. No metric, test answer or data change. |
| P7-09 | DOCUMENTATION / evidence limit | No compatible personal project and no organic human journal | Personal-project gate N/A with disclosure; human gate blocked, not inferred from synthetic successes. |
| P7-10 | LOW / closing documentation | Final root format check found one extra trailing blank line in the new audit document | Removed that line with a targeted patch, no automatic formatter. Repeated full verify543+5 and consumer6+1 passed; failed check log retained. |

No deterministic runtime dogfood bug, sensitive StackMate output, swallowed exception, behavior change, lifecycle
leak or external dependency-resolution defect was found in the tested flows. No new runtime feature, model,
worker, repository clone, framework upgrade or test-file change was made. Small-sample and synchronous-export
cost limitations remain; the user acceptance decision is still missing.
