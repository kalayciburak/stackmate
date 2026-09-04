# Phase 6 artifact audit

## Frozen publication allowlist

Only these coordinates may be included in a public RC bundle:

1. `io.github.kalayciburak:stackmate-parent`
2. `io.github.kalayciburak:stackmate-core`
3. `io.github.kalayciburak:stackmate-spring-boot-autoconfigure`
4. `io.github.kalayciburak:stackmate-spring-boot-starter`

Inference API/JVM, Jlama integration, local auto-configuration/starter, sample,
E2E, startup fixture, benchmark and research material are nonpublishable.

## RC artifact result

`-Ppublic-release clean install` passed for the parent, core, deterministic
auto-configuration and deterministic starter. Main artifacts and public SBOMs
were created. Sources and Javadoc JARs were created for core and
auto-configuration; they are not applicable to the dependency-only starter
because it has no Java source.

## Fail-closed bundle rules

`release/assemble-local-rc.ps1` rejects an allowlist other than the four frozen
coordinates, a version other than `0.1.0-rc.1`, missing required artifacts,
forbidden inference/research/model/native entries, missing JAR legal files,
forbidden public POM dependencies, and public SBOMs containing research/Jlama
dependencies. It writes a release audit and SHA-256 manifest without publishing
anything.

The final local bundle contains 24 files totaling 1,166,288 bytes. Its
archive-entry, public-POM and SBOM checks all passed. The bundle includes the
allowlist, API inventory and reproducibility report under `reports/`; no model,
inference, research, dataset, native or machine-local artifact is present.

| Public binary | SHA-256 |
|---|---|
| `stackmate-core-0.1.0-rc.1.jar` | `cccd092e8ab7366dc1bc991eb01d96e03160579efad50c1939f4f332977aa1dc` |
| `stackmate-spring-boot-autoconfigure-0.1.0-rc.1.jar` | `699ff017712454c9ef6175fd6ec71d161a61005bc20bacc3194b30db198f3a8f` |
| `stackmate-spring-boot-starter-0.1.0-rc.1.jar` | `7857638ea3ac8b338f89101ff8b0052fd5439a9e9666e3a0534c66cb84fa8cbb` |

```text
Public-release install       PASS
Public coordinates built     4/4
RC sources/Javadoc           PASS where applicable; starter N/A
RC SBOM generation           PASS
Final local RC assembly      PASS, target/release/stackmate-0.1.0-rc.1
Bundle files/bytes           24 / 1,166,288
Bundle manifest SHA-256      bc7ba3da624c6e4363cc7627ccf5c391f95c049193204bae4c03e80382a81f39
SHA256SUMS file SHA-256      4094de8427ec07278be83573ecd9f908b220b12636ff071b8af15e307e694fd5
Publication                  NOT PERFORMED
Signing                      NOT PERFORMED
```
