# Phase 7 automated external dogfood

Gate E: PASS. This is objective synthetic compatibility evidence, not organic human acceptance.

Candidate `0.1.0-rc.3`, source `8b40bac6dfb940324db638195766a67d5cdf7415`.
Two ordinary Java21 / Spring Boot4.1 MVC applications are stored outside this Git repository under the user's
StackMate dogfood directory. Both consume `io.github.kalayciburak:stackmate-spring-boot-starter:0.1.0-rc.3`
through a dedicated Maven local repository and an audited file repository, never a reactor classpath or source link.
The RC is not on Central. Resolver `_remote.repositories` records identify `stackmate-file` for its public coordinates.

| Application | Scenarios | Captured / supported | Correct category and root | Useful application frame |
| --- | ---: | ---: | ---: | ---: |
| Minimal quickstart | 5 | 5/5 | 5/5 | 5/5 |
| JDBC order service | 15 | 11/11 | 11/11 | 11/11 |
| Total | 20 | 16/16 | 16/16 | 16/16 |

The four uncaptured service cases are malformed JSON, Bean Validation, type conversion and custom ControllerAdvice
404. Their exceptions are resolved before escaping the servlet chain: documented boundaries, not silently omitted
misses. Both custom unknown failures produce UNKNOWN evidence, not a confident diagnosis.

The minimal quickstart was also run with no StackMate properties at all: default capture emitted GENERAL_JAVA,
HIGH evidence and the correct application method. A later detailed run enabled documented local export and package
ranking options. This does not retroactively claim export is enabled by default.

## Behavioral and security evidence

- Twenty distinct incidents executed in each of enabled, disabled and physically uninstalled modes:60 executions.
- Every corresponding HTTP status and exact UTF-8 body SHA-256 matched in all three modes; normal endpoint hashes
  also matched. Stable error bodies are an application choice shared by all modes, not a StackMate response override.
- Installed executable JARs contain exactly the three public StackMate JARs; uninstalled JARs contain none.
- All16 supported root exceptions remain available in original host stack traces; StackMate does not own them.
- No capture occurred in disabled/uninstalled modes. No confident false positive or sensitive canary match occurred.
- Seventy StackMate log/export files were scanned for synthetic password, bearer, email and database canaries.
  Raw host logs deliberately retain the synthetic exception: StackMate does not promise to sanitize host-owned logs.
- Repeating a supported case three times per enabled app emitted counts2/3/4 without creating new export bundles:
  quickstart5->5, service11->11. The18 repeats across both apps and all modes are not additional independent scenarios.
- Six JVMs exited gracefully with code0; no remaining owned sockets and no forced shutdown. Loopback network fixture
  threads were closed. Thread/socket snapshots showed only application/Tomcat/JDBC/loopback resources, not a worker.
- No StackMate runtime HTTP client, telemetry or model operation was introduced. Socket snapshots are bounded
  observation, not exhaustive packet capture or a proof against every possible transient network event.

PostgreSQL is the existing official17.11 image pinned to digest
`sha256:051f7b7b3abdd564d5d1bd1e8c4b9c1b6e77087d1dd22020ede611c096a272e0`, with a dedicated labeled container,
loopback-only port and synthetic database. No user database was touched. Fixed-width columns precede variable
columns in the two simple tables. The temporary refused/read-timeout sockets are also loopback-only.

## Timings

One startup per mode,20 normal requests,5/15 mixed failure requests,3 duplicate requests; milliseconds below.
P95 uses nearest-rank selection. Failure latency includes original application handling, logging and optional export.

| App / mode | Startup | Normal median / P95 | Failure median / P95 | Duplicate median | Graceful shutdown |
| --- | ---: | ---: | ---: | ---: | ---: |
| Quickstart enabled | 1695.93 | 1.02 / 1.65 | 15.14 / 103.29 | 5.01 | 230.57 |
| Quickstart disabled | 1620.23 | 1.04 / 1.23 | 2.67 / 34.89 | 2.18 | 233.64 |
| Quickstart uninstalled | 1615.85 | 0.87 / 1.05 | 2.24 / 35.19 | 1.75 | 230.37 |
| Service enabled | 2496.77 | 1.93 / 3.06 | 13.49 / 268.75 | 10.69 | 299.31 |
| Service disabled | 2446.54 | 2.33 / 3.08 | 3.27 / 268.85 | 4.52 | 289.62 |
| Service uninstalled | 2422.64 | 1.78 / 2.20 | 3.08 / 257.96 | 3.57 | 278.27 |

The service P95 includes a deliberately250ms network timeout. Cold first failure and synchronous sanitized export
are visible costs; these numbers are not an SLA or a statistically controlled regression test. The unchanged
Phase6.1-style hosted performance smoke also passed on the exact RC3 source. Different workloads are not pooled.

A separate opt-in `dogfood-metrics` profile delegates through the published `ServletExceptionCaptureHandler`
interface and reads public `DeduplicationStore.size()`. It does not replace the engine or access private fields;
the parity run above has no observer. Eleven first-seen capture calls measured6.74–57.55ms (cold JIT included),
and three duplicate capture calls6.73–7.26ms. This measures the full capture/diagnosis/render/export path, not the
engine alone. Cache size grew1..11 and stayed11 during duplicates; configured maximum remains512.
Graceful shutdown measurements include the application's deliberate200/250ms response-before-close delay.

Full per-case triggers, body hashes, roots, categories, evidence, frames, checks, outcomes, memory and timing samples
are in [automated-dogfood.json](evidence/automated-dogfood.json). Source/artifact identities are in the external
manifest. Harness defects and preserved failed attempts are documented in [dogfood-findings.md](dogfood-findings.md).
