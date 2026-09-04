# StackMate 0.1.0-rc.2 verification commands

Run from the repository root with JDK 21 in `JAVA_HOME`. These are verification commands, not publication commands.

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify

.\mvnw.cmd --batch-mode --no-transfer-progress `
  -Ppublic-release `
  -pl stackmate-core,stackmate-spring-boot-autoconfigure,stackmate-spring-boot-starter `
  -am clean install

.\mvnw.cmd --batch-mode --no-transfer-progress `
  -f release\consumer\pom.xml `
  "-Dstackmate.version=0.1.0-rc.2" clean verify

.\release\generate-public-api.ps1 `
  -Version 0.1.0-rc.2 `
  -SignatureOutput target\qualification\public-api.txt `
  -InventoryOutput target\qualification\public-api.json

.\release\assemble-local-rc.ps1 `
  -Version 0.1.0-rc.2 `
  -OutputRoot target\release
```

Remote workflows run the OS-equivalent commands and upload controlled evidence for seven days. `release-sign`,
`deploy`, Central credentials and GitHub release commands are intentionally absent.
