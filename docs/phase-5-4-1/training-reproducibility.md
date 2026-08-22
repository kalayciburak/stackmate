# Phase 5.4.1 training reproducibility

The only real run was `P5.4-T1-real-world-r8` with the exact frozen plan,
`PYTORCH_CUDA_ALLOC_CONF=expandable_segments:True`, seed `520021`, 140 TRAIN
rows, 40 VALIDATION rows, six epochs, and 54 optimizer steps. It used the
official BF16 base and the inspected 12-module target list.

Training report SHA-256:
`fb2177281247c1d356c62eea898a03aa6b6da3b55ee9855e19bfdfd6b3566ea3`.

The selected checkpoint was checkpoint 18, selected only from validation. The
full run had finite loss/gradient history, 7,118,163,968-byte peak allocated
VRAM, 7,667,187,712-byte peak reserved VRAM, and adapter hash
`7848c9a140d2616e009b5179aa4a02867590f95dd261154c0b983ee45c187b49` for the
adapter-only selected copy.

No resume checkpoint, third training run, validation leakage, locked-final data,
remote tracker, or network fallback was used.
