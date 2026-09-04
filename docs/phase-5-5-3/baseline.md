# Phase 5.5.3 baseline

Phase 5.5.3 resumes from the completed Phase 5.5.2 negative-result checkpoint.

```text
Starting branch       phase-5-5-2-atomic-contrastive-dataset
Starting HEAD         01650cf45d9a3c0acae388aed0235f6227f1ab6d
Completion tag        phase-5-5-2-complete
New branch            phase-5-5-3-restricted-logit-binary-lora
Working tree          clean
Remote                origin configured; no push
```

Integrity bindings:

```text
Official base revision  15852e8c16360a2fea060d615a32b45270f8a8fc
Base manifest SHA-256   1dc49db3fd0a49ee7bf33190b04c1bac5d1ed489e87855a81c64ac7344571981
Ontology SHA-256        f24f0128fbcba5754888f1abd7484f4b5530f2219da431d60923cafe16a1e793
Dataset manifest        3a48c4e4721b5b7cb590ff16f7b95ef1afaa557a439f12dc6002065df260f67a
TRAIN SHA-256           cb5f1ef1611b4f4383226dcc0fb10729cb2bac1aafacfb374cc29141f1769313
VALIDATION SHA-256      d6496481d7073a76605c744f11dd03e0d8a3fcc79ff9e8418bba187391373701
Frozen base report      cd01c7696ffa05b2382af6a71c12a194baa2c24c5741a18fae392de712af57ea
```

Machine and verification:

```text
Windows                 10.0.26200.8973
WSL                     2.7.12.0 / kernel 6.18.33.2
Distribution            Ubuntu 22.04.5 LTS
GPU                     NVIDIA RTX 4070 Ti SUPER, 16,376 MiB
Linux filesystem free   925 GiB
WSL memory              30 GiB total
uv                      0.12.1
Python tests            56/56 PASS
Surefire                 517 PASS
Failsafe                 11 PASS
Failures/errors/skips    0/0/0
Spotless/coverage/Enforcer PASS
Tracked model-like files 0
Running StackMate ML jobs 0
New packages             none
```

The first Maven invocation did not start because the inherited `JAVA_HOME`
value was malformed. The successful run used the already installed Corretto
21.0.12 through a process-local `JAVA_HOME`; no global setting or dependency
was changed. The product build used no WSL, Python, CUDA, model, adapter, GGUF
or cloud service.

The Phase 5.5.2 base metrics remain an immutable comparator. They are read from
the frozen raw-logit report rather than regenerated with another GPU run.
