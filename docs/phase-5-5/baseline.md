# Phase 5.5 baseline

Phase 5.5 starts from the completed Phase 5.4.3 negative listwise result.

```text
Starting branch    phase-5-4-3-fresh-final-t1-qualification
Starting HEAD      fc34177e6fb7acc1a9e5ebf3dfdcf1ff29439e0b
Completion tag     phase-5-4-3-complete
New branch         phase-5-5-pairwise-evidence-entailment
Working tree       clean
```

Integrity bindings:

```text
Official base revision  15852e8c16360a2fea060d615a32b45270f8a8fc
Base manifest SHA-256   1dc49db3fd0a49ee7bf33190b04c1bac5d1ed489e87855a81c64ac7344571981
Historical adapter      P5.4.1-T1-checkpoint-18
Adapter SHA-256         7848c9a140d2616e009b5179aa4a02867590f95dd261154c0b983ee45c187b49
Selection commit        08b5b55adee82a83c3d81e599deca8ca6b3458ca
TRAIN rows SHA-256      ee52ae93457b9dc7f663f83b1acea37df6c25f437ac84261c951844a5965ba93
VALIDATION rows SHA-256 bc8e2b5573c57f6927f0e8547968e29fa3bf0f24b221d7bb402b13ba04680789
Dataset manifest        0e2e23241b582fb4f7c5fb25b87a5f08016f28f8be0c7525595e170467f5d2b2
TRAIN family-set hash   5babe3ba3a27254f1d6a7d9083d7d21bcbbbf9f07ee101ebfa0037823c43b9ae
VALIDATION family hash  b521d352327c66fac547ef46e1bb0e8f26d6ba9bd73dc41b15a4e4d571a6d5c6
Fresh-final result      097ab671d2cc6d37c128b4a42259a34c3982cadefb378ab0013567aaae299c1b
```

Environment:

```text
Host                 Windows 11
Research OS          WSL2 Ubuntu
GPU                  NVIDIA RTX 4070 Ti SUPER, 16,376 MiB
Official base path   verified in external WSL cache
Tracked model files  0
Running StackMate ML processes 0
New packages         none
```

Verification:

- Frozen Python environment: 18/18 tests passed.
- Normal Maven build: 517 Surefire and 11 Failsafe tests passed, with zero
  failures, errors, or skips.
- The first Maven attempt reached 10/11 Failsafe tests but one sample startup
  lost an ephemeral port race on port 52658. The port was already free when
  inspected; no process was killed. A complete clean rerun passed.

No model or adapter was loaded by Gate A. The historical listwise adapter is
diagnostic evidence only and will not initialize a pairwise adapter.
