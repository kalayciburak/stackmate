# Phase 5.5.2 reproducibility

Phase 5.5.2 starts at
f76dc983efac08c8b66123f7e4cb13fdfba492bb and uses the local
phase-5-5-1-complete annotated tag. Pre-model artifacts were committed before
Gate E.

Key bindings:

~~~text
Base revision        15852e8c16360a2fea060d615a32b45270f8a8fc
Ontology             f24f0128fbcba5754888f1abd7484f4b5530f2219da431d60923cafe16a1e793
Dataset manifest     3a48c4e4721b5b7cb590ff16f7b95ef1afaa557a439f12dc6002065df260f67a
Dataset audit        48e03e8ab1d124735371723a359738337e3867fb29a31144b7d6791c9214848d
Shortcut audit       8ca37d5fb2acf706d2cde94903256acaaf6a5428c222718c269c1b394547a0b0
Tokenization audit   b500a6125890add28c15ddee0246447d40b5208d65de5af8a41f52b8600e23d7
Base result          cd01c7696ffa05b2382af6a71c12a194baa2c24c5741a18fae392de712af57ea
~~~

Dataset generation uses seed 5502001, canonical sorted JSON, LF line endings,
CREATE_NEW publication and two deterministic predicate-recomputation orders.
Two in-memory builds were byte-identical before publication.

Gate E command used:

~~~bash
PYTORCH_CUDA_ALLOC_CONF=expandable_segments:True \
TOKENIZERS_PARALLELISM=false \
CUDA_VISIBLE_DEVICES=0 \
uv run --frozen python scripts/evaluate_atomic_binary_base.py \
  --base-model '<external-training-cache>/<phase-subdir>/models--Qwen--Qwen3.5-2B/snapshots/15852e8c16360a2fea060d615a32b45270f8a8fc' \
  --batch-size 8 \
  --maximum-sequence 1024 \
  --report reports/phase-5-5-2-base-feasibility.json
~~~

The process returned the methodological stop BINARY_BASE_NOT_PROMISING. No retry,
prompt correction, training, adapter, calibration or exposed evaluation followed.

Final verification:

~~~text
uv lock --check                         PASS, 64 packages
python -m py_compile scripts/*.py ...  PASS
python -m unittest discover -s tests  PASS, 56/56
mvnw clean verify                      PASS, 517 Surefire + 11 Failsafe
Spotless / coverage / Enforcer         PASS
~~~

No Java or POM file changed after the successful normal build.
