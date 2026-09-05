# Phase 7 commands and actual outcomes

Commands below use semantic path variables, not private machine paths. Windows commands use process-local Java21
and `login:false`/a profile-free PowerShell. No permanent JAVA_HOME change or package upgrade was needed.

```powershell
$env:JAVA_HOME = '<jdk-21-home>'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
git status --short --branch
git rev-parse HEAD
git fetch --prune origin
git switch main
git merge --ff-only 56dc1f8452da90f558807790c20d7cf1eac0fc00
git push -u origin main
gh repo edit kalayciburak/stackmate --default-branch main
git remote set-head origin -a
gh pr comment 1 --body-file '<external-evidence>/pr1-closure.md'
gh pr close 1
gh api --method PUT repos/kalayciburak/stackmate/branches/main/protection --input '<external-evidence>/main-protection.json'
git switch -c phase-7-repository-dogfood
```

All completed. The protection JSON is described in [repository-topology.md](repository-topology.md); old branches
were not deleted, no force push occurred, and private visibility stayed unchanged.

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
.\mvnw.cmd --batch-mode --no-transfer-progress -Ppublic-release `
  -pl stackmate-core,stackmate-spring-boot-autoconfigure,stackmate-spring-boot-starter -am install
.\mvnw.cmd --batch-mode --no-transfer-progress -f release/consumer/pom.xml `
  '-Dstackmate.version=0.1.0-rc.3' clean verify
.\release\generate-public-api.ps1 -Version 0.1.0-rc.3 `
  -SignatureOutput target/qualification/public-api.txt -InventoryOutput target/qualification/public-api.json
.\release\assemble-local-rc.ps1 -Version 0.1.0-rc.3 -OutputRoot target/qualification-release
.\release\collect-qualification-evidence.ps1 -Platform Windows-phase7-final -Version 0.1.0-rc.3 `
  -BundlePath target/qualification-release/stackmate-0.1.0-rc.3
.\release\verify-file-repository.ps1 -Version 0.1.0-rc.3 -OutputRoot '<external-evidence>/rc3/file-repository'
```

RC2 baseline and RC3: full543+5, separate consumer6+1, API73 unchanged, public install and file repository13 payloads
+26 checksum sidecars passed. Recorded invocation/format failures are preserved in [findings](dogfood-findings.md).
No `spotless:apply`, Python/ML runtime build integration, test-file edit or Maven dependency upgrade was performed.

```powershell
git push -u origin phase-7-repository-dogfood
gh pr create --draft --base main --head phase-7-repository-dogfood `
  --title 'Phase 7: RC3 ve repo dışı dogfood hazırlığı' --body-file '<external-evidence>/phase7-pr-body.md'
gh workflow run release-preflight.yml --ref phase-7-repository-dogfood
gh run download 33962139971 --dir '<external-evidence>/hosted-rc3-ci'
gh run download 33962139468 --dir '<external-evidence>/hosted-rc3-preflight'
```

Source commit8b40bac was pushed; draft PR2 opened. Exact-source hosted CI, CodeQL, dependency review and manual
preflight all passed. Evidence was downloaded locally; no model/dataset/app source was uploaded.

## External application preparation and execution

`$Dogfood` denotes the external `v0.1.0-rc.3` dogfood directory. Its `settings.xml` points to the audited local
file repository and Maven Central for ordinary dependencies. `$Dogfood/m2` is isolated from the default Maven cache.
The existing Docker Desktop was started; no Docker/PostgreSQL package or image download was needed.

```powershell
docker desktop start --detach
docker run --detach --name stackmate-phase7-dogfood-pg --label stackmate.scope=phase7-dogfood `
  --publish 127.0.0.1::5432 --env POSTGRES_DB=dogfood --env POSTGRES_USER=dogfood `
  --env POSTGRES_PASSWORD=synthetic-dogfood-only `
  postgres@sha256:051f7b7b3abdd564d5d1bd1e8c4b9c1b6e77087d1dd22020ede611c096a272e0
docker port stackmate-phase7-dogfood-pg 5432/tcp
.\mvnw.cmd --batch-mode --no-transfer-progress -s "$Dogfood/settings.xml" `
  "-Dmaven.repo.local=$Dogfood/m2" -f "$Dogfood/quickstart/pom.xml" clean package
.\mvnw.cmd --batch-mode --no-transfer-progress -s "$Dogfood/settings.xml" `
  "-Dmaven.repo.local=$Dogfood/m2" -f "$Dogfood/quickstart/pom.xml" '-P!with-stackmate' clean package
# Repeat the two package commands for service-consumer; preserve each resulting JAR under artifacts/.
& "$Dogfood/run-dogfood.ps1" -JavaHome $env:JAVA_HOME -Applications quickstart -Modes enabled -Minimal
# Set DOGFOOD_DB_URL/USER/PASSWORD to the dedicated loopback fixture for service runs.
& "$Dogfood/run-dogfood.ps1" -JavaHome $env:JAVA_HOME
# Separately package service-consumer-metrics.jar with the optional observer class.
& "$Dogfood/run-dogfood.ps1" -JavaHome $env:JAVA_HOME -Applications service-consumer -Modes enabled -Metrics
```

All four parity packages and the separate metrics package built. Three-mode run `20260905-140506-295` and metrics
run `20260905-140730-847` passed. Every child process was scoped, hidden and stopped; no global sleep/power/driver
configuration changed. Source/script/JAR SHA-256 values are in [external-app-manifest.json](evidence/external-app-manifest.json).

For later normal development the prepared external `start-service.ps1 -JavaHome <jdk-21-home>` starts the existing
synthetic database and the installed service in the caller's console, with local diagnostics and no remote data
sharing. `-Mode disabled` or `-Mode uninstalled` selects the prepared alternatives. The user must not count the
scripted failure endpoints as organic journal entries.

## Closing boundary

The dedicated PostgreSQL container was stopped and retained for restart; all dogfood JVMs exited. Docker Desktop
itself remains available. No unrelated container/image/cache was deleted. `v0.1.0-rc.3` was created as a local
annotated tag at the exact qualified source, with no tag push. The closing commit contains only reviewed evidence,
the empty user journal and current-documentation path neutralization. The Phase7 draft remains unmerged.
