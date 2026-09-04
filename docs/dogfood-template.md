# StackMate public-alpha dogfood report

Use synthetic or approved non-sensitive data. Do not paste credentials, personal/customer data, proprietary source,
unredacted production logs, or an unreviewed incident bundle.

## Environment

- StackMate version/revision:
- Operating system:
- Java vendor/version:
- Spring Boot version:
- Spring Framework version:
- Servlet container:
- Packaging and launch command:
- Relevant non-secret `stackmate.*` settings:

State whether this matches the locally verified Windows 11 / Corretto 21.0.12.1 / Boot 4.1.0 / Framework 7.0.8 /
Spring MVC baseline. Do not label another platform supported merely because one scenario worked.

## Scenario

- Synthetic failure type:
- Endpoint method and path, if safe:
- Expected host HTTP status/response behavior:
- Expected StackMate category/evidence behavior:
- Expected sanitization behavior:
- Expected duplicate/export behavior:

## Observation

- Did the exception escape the MVC servlet chain?
- Did StackMate render output?
- Did host HTTP behavior remain unchanged?
- Were all StackMate-controlled values sanitized?
- Was an unknown diagnosis explicit rather than speculative?
- Did a repeat produce the expected bounded duplicate behavior?
- If export was enabled, were exactly four recognized files created atomically?
- Were there unexpected network, telemetry, download, upload, model, or inference actions?

## Evidence

Paste only a minimal sanitized excerpt. Mark hand-written examples as `REPRESENTATIVE`; mark actual captured output
as `OBSERVED` and record how it was produced. Do not convert a local observation into a general compatibility or
test-pass claim.

```text
<sanitized evidence>
```

## Result

- Result: `PASS`, `FAIL`, or `INCONCLUSIVE`
- Reproduction frequency:
- Regression from `stackmate.enabled=false` comparison:
- Open question or suspected boundary:
- Safe minimal reproduction location:

Security-sensitive findings follow [../SECURITY.md](../SECURITY.md). General usage and compatibility findings follow
[../SUPPORT.md](../SUPPORT.md).
