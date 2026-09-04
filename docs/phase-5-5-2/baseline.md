# Phase 5.5.2 baseline

Phase 5.5.2 starts from the completed Phase 5.5.1 dataset-blocked result.

```text
Starting branch       phase-5-5-1-binary-evidence-support
Starting HEAD         f76dc983efac08c8b66123f7e4cb13fdfba492bb
Completion tag        phase-5-5-1-complete
New branch            phase-5-5-2-atomic-contrastive-dataset
Obsolete state        not detected
Working tree          clean
Remote                origin configured; no push
```

Integrity bindings:

```text
Official base revision   15852e8c16360a2fea060d615a32b45270f8a8fc
Base manifest SHA-256    1dc49db3fd0a49ee7bf33190b04c1bac5d1ed489e87855a81c64ac7344571981
TRAIN SHA-256            ee52ae93457b9dc7f663f83b1acea37df6c25f437ac84261c951844a5965ba93
VALIDATION SHA-256       bc8e2b5573c57f6927f0e8547968e29fa3bf0f24b221d7bb402b13ba04680789
Phase 5.5.1 audit SHA-256 18a489fbcd86f41e9d8d8e111fd192a0a1e1e336452e1286b37ece5ae6db6c7d
```

Verification:

```text
WSL research tests        36/36 PASS
Surefire                  517 PASS
Failsafe                  11 PASS
Failures/errors/skips     0/0/0
Spotless/coverage/Enforcer PASS
Tracked model-like files  0
Running StackMate ML jobs 0
New packages              none
```

The normal build used the existing Corretto 21 installation through a
process-local `JAVA_HOME`. It required no WSL, Python, CUDA, model, adapter, GGUF
or cloud service.
