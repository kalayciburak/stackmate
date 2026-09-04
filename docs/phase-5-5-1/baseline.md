# Phase 5.5.1 baseline

Phase 5.5.1 starts from the completed Phase 5.5 blocked tri-class result.

```text
Starting branch       phase-5-5-pairwise-evidence-entailment
Starting HEAD         5585a59c36d68fab45887688453d6b19b9ec7a8f
Completion tag        phase-5-5-complete
New branch            phase-5-5-1-binary-evidence-support
Obsolete branch found no
Working tree          clean
Remote                origin configured; no push
```

Integrity bindings:

```text
Official base revision  15852e8c16360a2fea060d615a32b45270f8a8fc
Base manifest SHA-256   1dc49db3fd0a49ee7bf33190b04c1bac5d1ed489e87855a81c64ac7344571981
TRAIN SHA-256           ee52ae93457b9dc7f663f83b1acea37df6c25f437ac84261c951844a5965ba93
VALIDATION SHA-256      bc8e2b5573c57f6927f0e8547968e29fa3bf0f24b221d7bb402b13ba04680789
Tri-class audit SHA-256 cb9f4d734542c361ff76c7eaa90b261d308fe8aff093f0a9150cc7bb1fda413c
```

Environment and verification:

```text
Host                       Windows 11
Research OS                Ubuntu 22.04.5 LTS under WSL2
GPU                         NVIDIA RTX 4070 Ti SUPER, 16,376 MiB
Windows driver              616.56
Official base path          present in external WSL cache
Python frozen tests         24/24 PASS
Surefire                    517 PASS
Failsafe                    11 PASS
Failures/errors/skips       0/0/0
Spotless/coverage/Enforcer  PASS
Tracked model-like files    0
Running StackMate ML jobs   0
New packages                none
```

The normal Maven build used the existing Corretto 21 installation through a
process-local `JAVA_HOME`. No system environment variable changed. The residual
Windows-side frozen research environment recorded by Phase 5.5 remains outside
Git and was not used.
