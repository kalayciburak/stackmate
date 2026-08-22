# Locked-final access control result

The final corpus was opened only after the validation-only adapter selection
commit. The runner verified the base revision, base manifest, selected adapter
hash, selection report hash, selection commit, sealed descriptor hash, input
byte hash, stage order, and 20/20/30 cardinalities before generation.

The external receipt was created with exclusive file creation:

```text
Input SHA-256: e7323f558fbe968711daa1075ed106a64f9e8d66e12d2292153bb6410d792777
Receipt SHA-256: 502bfbfcae48474c752b3ac8b142ecd19d76f4d67cc9944f4d52952dc6e6f81f
Raw report SHA-256: 6a222a12dfea9d612510ded7861893f7f3f8dc1b12af47cef927da14033a1b60
Authoritative report SHA-256: 83c9218f7733f91d4911254aa872217fbccc24ec7b029c74337594b56e782ea3
```

No second locked execution was attempted. The access receipt is external and
the raw prediction report is not committed. The selected adapter report remains
validation-only and was not rewritten with locked results.
