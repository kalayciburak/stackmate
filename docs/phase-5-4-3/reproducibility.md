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
