# Phase 5.5.3 reproducibility

Phase 5.5.3 is bound to branch
`phase-5-5-3-restricted-logit-binary-lora`, starting commit
`01650cf45d9a3c0acae388aed0235f6227f1ab6d`, and Gate B commit
`5acdbfd259e64772c017ead09278b21422d2250a`. Work remained local-only and no
remote push was performed.

Gate A Python commands ran in the existing WSL environment from
`research/model-alignment`:

```bash
export UV_PROJECT_ENVIRONMENT="$HOME/.cache/stackmate/environments/phase-5-2"
uv lock --check
uv run --frozen python -m py_compile scripts/*.py tests/*.py
uv run --frozen python -m unittest discover -s tests -v
```

The initial Gate A suite passed with uv 0.12.1 and 56/56 tests. After the seven
Phase 5.5.3 fixture tests were added, the same frozen commands passed 63/63.
The successful Maven verification used the existing Corretto 21.0.12 only for
that PowerShell process:

```powershell
$env:JAVA_HOME = '<jdk-21-home>'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& "$env:JAVA_HOME\bin\java.exe" -version
.\mvnw.cmd clean verify
```

It passed 517 Surefire and 11 Failsafe tests with zero failures, errors or
skips; Spotless, coverage and Enforcer checks passed. The first Maven attempt
did not start because the inherited `JAVA_HOME` was malformed.

Gate B audit command ran in the same WSL environment from
`research/model-alignment`:

```bash
export UV_PROJECT_ENVIRONMENT="$HOME/.cache/stackmate/environments/phase-5-2"
uv run --frozen python scripts/audit_phase553_deterministic_solvability.py \
  --report reports/phase-5-5-3-deterministic-solvability.json
```

Reproducibility bindings:

```text
Ontology                     f24f0128fbcba5754888f1abd7484f4b5530f2219da431d60923cafe16a1e793
Dataset manifest             3a48c4e4721b5b7cb590ff16f7b95ef1afaa557a439f12dc6002065df260f67a
TRAIN                        cb5f1ef1611b4f4383226dcc0fb10729cb2bac1aafacfb374cc29141f1769313
VALIDATION                   d6496481d7073a76605c744f11dd03e0d8a3fcc79ff9e8418bba187391373701
Phase 5.5.2 base report      cd01c7696ffa05b2382af6a71c12a194baa2c24c5741a18fae392de712af57ea
Historical D0 report         097ab671d2cc6d37c128b4a42259a34c3982cadefb378ab0013567aaae299c1b
Gate B auditor               cc42908a7f196ce179bdd6e275ac0f0aa6c83a406ee5b3bd13a9302a7891db5d
TRAIN prediction receipt     0ecec7719bdc3300fde2125efe2a4d273e479d134d31bf035d57c90675a03ee5
VALIDATION prediction receipt 42d2ae12069def236347cbfafbbbb58766c84442678ae30f34031955e1a357ec
Combined prediction receipt  56de9ea7be8495ac3c584013e681134714aa5f34e6b33486220cc07be11e237e
Gate B report                43f6ef5de6222e1a93bc29b26544c67c0ff8ec7bae023fd01b7d6d62ffe9d426
```

The audit was local and offline: no upload, network, telemetry, package
installation, model load or GPU use occurred. The existing Phase 5.5.2 raw
base report was reproduced from its stored pair results without rerunning the
model. No formatter was run.
