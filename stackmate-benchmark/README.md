# StackMate benchmark module

Normal Maven verification runs the deterministic 50-case corpus and writes reports whose model
modes are explicitly marked `NOT_EXECUTED`. It does not load or download model weights.

Run the complete opt-in A/B/C local-model benchmark from the repository root with a model that is
already present on disk:

```powershell
.\mvnw.cmd -pl stackmate-benchmark -am -Pmodel-benchmark `
  -Dstackmate.model.path=C:\absolute\path\to\Qwen2.5-0.5B-Instruct `
  verify
```

On Unix-like systems:

```bash
./mvnw -pl stackmate-benchmark -am -Pmodel-benchmark \
  -Dstackmate.model.path=/absolute/path/to/Qwen2.5-0.5B-Instruct \
  verify
```

The profile reuses a managed Jlama child JVM for up to 100 model invocations; validation failures, timeouts, and
circuit breaking can reduce the actual generated count. It writes:

- `stackmate-benchmark/target/stackmate-benchmark-report.json`
- `stackmate-benchmark/target/stackmate-benchmark-report.md`

Optional properties are `-Dstackmate.benchmark.language=tr`,
`-Dstackmate.benchmark.maximum-cases=5`, `-Dstackmate.benchmark.output-directory=...`, and
`-Dstackmate.benchmark.timeout-seconds=60`. Mode A receives raw sanitized bounded stack text, the
same global category taxonomy for every case, and a source allow-list used only to reject fabricated
locations. It deliberately receives no case-specific cause or check IDs, so those two Mode A metrics
are reported as `NOT_APPLICABLE`. Mode B uses the production deterministic request factory, managed
Jlama engine, and request-scoped output validator. The harness does not measure process-tree RSS;
that requires a separate operating-system sampler and is reported as `NOT_MEASURED`.
