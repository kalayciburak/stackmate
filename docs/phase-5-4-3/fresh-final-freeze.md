# Phase 5.4.3 fresh-final freeze

Gate D froze the 30-family T1 corpus before any D0, B0, or A1 generation.
The canonical report and benchmark-resource copies of the root manifest are
byte-identical.

```text
Root manifest SHA-256  6364857bef7b9fc4a2c0eb60acf0e813093a0137064e7df1a17f3684e59c3139
Generation inputs      8d1020d58bb31303bc6649a6a7088084091d80d5ae3287eb289220e20b02ffe8
Scoring labels         c44e481fe9ea1a019e1bfdc7bd1360428580b98808ccc605e5c82dc224e10fd3
Resolved descriptors   59d518e2a17dca84266bceb29048ec5b0fd6917bbc5ee7b85336f5fa4b9318d5
Reproduction report    02a57674877f28617a16ae87aaa3097993d02dfec980766f441b4cd2ddb92435
```

Frozen identity bindings:

```text
Base repository        Qwen/Qwen3.5-2B
Base revision          15852e8c16360a2fea060d615a32b45270f8a8fc
Base manifest          1dc49db3fd0a49ee7bf33190b04c1bac5d1ed489e87855a81c64ac7344571981
Adapter                 P5.4.1-T1-checkpoint-18
Adapter SHA-256         7848c9a140d2616e009b5179aa4a02867590f95dd261154c0b983ee45c187b49
Selection commit        08b5b55adee82a83c3d81e599deca8ca6b3458ca
T1 prompt               9daee6dc707ff1c347346f5f0c667cba2b03035ab3966ddaf362744e0c528897
T1 schema               efb7f14276ddb108d5111622a92f3e5730b8cb3724336fdc913a4efc0c6ccb52
Generation config       9625c62a83aab30e1a5f788c8bf164df4074fb9ace181df626ab398b0dc8392b
Generation runner       4f3adf07d49c4a80d25f5b3fd6d99da0d49fad99188c8ab4edd9b953a189045c
Scorer                   6ab28056bbc5bba0e1f86eea98801b2c1dbe3037ee62c99739933c1fbe848637
```

The freeze commit is identified by the annotated local tag
`phase-5-4-3-fresh-final-frozen`; the generator requires HEAD to equal that tag
and verifies the manifest blob directly from the tagged commit. No model output
existed when the manifest was created.
