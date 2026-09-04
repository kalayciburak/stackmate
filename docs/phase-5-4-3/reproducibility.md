# Phase 5.4.3 reproducibility

The authoritative Windows repository remains the working tree. Large model,
adapter, prediction, merged, and GGUF artifacts remain in external WSL or local
cache paths.

Corpus reproduction uses the explicit `FreshFinalV2CorpusExportLab` with a new
output directory. It refuses overwrite. Two complete final exports produced the
same hashes:

```text
Resolved descriptors 59d518e2a17dca84266bceb29048ec5b0fd6917bbc5ee7b85336f5fa4b9318d5
Generation inputs   8d1020d58bb31303bc6649a6a7088084091d80d5ae3287eb289220e20b02ffe8
Scoring labels      c44e481fe9ea1a019e1bfdc7bd1360428580b98808ccc605e5c82dc224e10fd3
Reproduction       02a57674877f28617a16ae87aaa3097993d02dfec980766f441b4cd2ddb92435
```

Research validation uses the frozen environment:

```bash
export UV_PROJECT_ENVIRONMENT="$HOME/.cache/stackmate/environments/phase-5-2"
uv lock --check
uv run --frozen python -m py_compile scripts/*.py tests/*.py
uv run --frozen python -m unittest discover -s tests -v
```

Normal product validation is Windows-local and model-free:

```powershell
$env:JAVA_HOME='C:\Program Files\Amazon Corretto\jdk21.0.12_9'
.\mvnw.cmd clean verify
```

The final pre-generation run passed 517 Surefire and 11 Failsafe tests with no
failure, error, or skip. The isolated Python suite passed 17/17 tests.

Exactly-once generation must use the committed and annotated freeze tag. The
runner order is D0, B0, A1. No score may be opened until all three receipts are
complete. Scoring may be repeated against immutable predictions without loading
the model again.

Exactly-once prediction hashes:

```text
D0 8b242f05890d9fad9d2e1b2317dff17e162a81baaecbe6f965d7228c24460b3f
B0 8dc4647ad61bf314d9ed0ba0005a1556d1ca6003fa1b5df0fb8fb84988ca682d
A1 52580204f5c18d2f5926ffa453948f6c2c21af6efe353ec20f3288182e4c9123
```

The frozen scorer's first attempt exposed a nullable-invalid-output aggregation
bug and wrote no report. The documented scorer-only wrapper verified the frozen
scorer SHA, mapped only nullable accuracy values to `false`, and scored the same
immutable predictions. No generation or label was repeated or changed. See
`phase-5-4-3-scoring-rerun.json`.
