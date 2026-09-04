# StackMate 0.1.0-rc.1 verification commands

Run from the repository root in PowerShell with `JAVA_HOME` pointing to JDK 21.
These commands are a release checklist; their presence is not evidence that
they ran. Use empty bundle output roots and preserve every generated report.

## 1. Final full reactor verification

This rerun is mandatory after removing the stale `stackmate.console.ansi`
property from the sample E2E configuration.

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Record the exact Surefire/Failsafe totals and any failure, error or skip counts.

## 2. Build and install public RC artifacts

Recorded result: `PASS`.

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Ppublic-release clean install
```

## 3. Verify the independent external consumer

Recorded result: `PASS`, 6/6 contract plus 1/1 packaged process.

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -f release\consumer\pom.xml `
  -Dstackmate.version=0.1.0-rc.1 verify
```

## 4. Regenerate and compare the public API

Recorded result: 73 types and SHA-256
`41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7`.

```powershell
.\release\generate-public-api.ps1 `
  -Version 0.1.0-rc.1 `
  -SignatureOutput target\release-api-current.txt `
  -InventoryOutput target\release-api-current.json

$expectedApiHash = (Get-Content release\public-api-inventory.json -Raw |
  ConvertFrom-Json).signatureSha256
$actualApiHash = (Get-FileHash target\release-api-current.txt -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualApiHash -ne $expectedApiHash) {
  throw "Public API signature drift: $actualApiHash"
}
```

## 5. Performance evidence

The final RC JMH run is recorded in `performance.md`. The HTTP and startup
commands reproduce their separately recorded snapshots.

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl stackmate-benchmark -am `
  -Pdeterministic-performance `
  -DskipTests `
  "-Djacoco.skip=true" verify

$fixtureRoot = Join-Path $env:TEMP "stackmate-phase-6-performance-fixtures"
New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null

.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl stackmate-startup-baseline -am -DskipTests clean package
Copy-Item `
  stackmate-startup-baseline\target\stackmate-startup-baseline-0.1.0-rc.1.jar `
  (Join-Path $fixtureRoot "without-stackmate.jar") -Force

.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl stackmate-startup-baseline -am `
  -Pwith-deterministic-stackmate -DskipTests clean package
Copy-Item `
  stackmate-startup-baseline\target\stackmate-startup-baseline-0.1.0-rc.1.jar `
  (Join-Path $fixtureRoot "deterministic.jar") -Force

.\stackmate-benchmark\scripts\measure-http.ps1 `
  -BaselineJar (Join-Path $fixtureRoot "without-stackmate.jar") `
  -DeterministicJar (Join-Path $fixtureRoot "deterministic.jar") `
  -WarmupIterations 20 -Iterations 200

.\stackmate-benchmark\scripts\measure-startup.ps1 `
  -BaselineJar (Join-Path $fixtureRoot "without-stackmate.jar") `
  -DeterministicJar (Join-Path $fixtureRoot "deterministic.jar") `
  -WarmupIterations 2 -Iterations 10

.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl stackmate-benchmark -am -DskipTests compile
.\stackmate-benchmark\scripts\measure-cache-memory.ps1 `
  -Iterations 7 -MaximumEntries 10000
```

## 6. Final public SBOM OSV scan

Recorded result: exit 0, zero known vulnerability groups; core 0,
auto-configuration 18 and starter 14 packages scanned. The official
OSV-Scanner 2.5.1 Windows executable had SHA-256
`25e42f5ef6711fd8c0fb45390972205891dd44c6bd02ac93f0f63e8e98d9bfb6`.

```powershell
$osvScanner = "<verified-osv-scanner-2.5.1-executable>"
& $osvScanner scan source `
  --format json `
  --output-file target\phase-6-public-osv.json `
  --sbom stackmate-core\target\bom.json `
  --sbom stackmate-spring-boot-autoconfigure\target\bom.json `
  --sbom stackmate-spring-boot-starter\target\bom.json
```

`--sbom` emitted a deprecation warning in 2.5.1 but completed successfully.
Preserve the scanner checksum, command, input SBOMs and output JSON together.

## 7. Assemble and compare two local RC bundles

```powershell
.\release\assemble-local-rc.ps1 `
  -Version 0.1.0-rc.1 `
  -OutputRoot target\release-first

.\release\assemble-local-rc.ps1 `
  -Version 0.1.0-rc.1 `
  -OutputRoot target\release-second

.\release\compare-release-bundles.ps1 `
  -First target\release-first\stackmate-0.1.0-rc.1 `
  -Second target\release-second\stackmate-0.1.0-rc.1 `
  -Report target\release-comparison.json
```

Inspect both `reports/release-audit.json` files, both checksum manifests and the
comparison report. This is local assembly only; do not activate `release-sign`,
create a tag, push or publish as part of verification.

## 8. Remote evidence

After an authorized push, record links and conclusions for CI on Windows/Linux,
macOS smoke, CodeQL, dependency review and release preflight. Until observed,
each remote result remains `UNVERIFIED`.
