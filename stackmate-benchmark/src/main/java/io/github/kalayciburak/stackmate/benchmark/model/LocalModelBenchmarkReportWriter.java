package io.github.kalayciburak.stackmate.benchmark.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.kalayciburak.stackmate.benchmark.corpus.BenchmarkCorpus;
import io.github.kalayciburak.stackmate.benchmark.evaluation.DeterministicEvaluationHarness;
import io.github.kalayciburak.stackmate.benchmark.evaluation.EvaluationSummary;
import io.github.kalayciburak.stackmate.inference.ModelDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Writes explicit A/B/C benchmark results without committing machine-specific output. */
public final class LocalModelBenchmarkReportWriter {

    public static final String JSON_FILE_NAME = "stackmate-benchmark-report.json";
    public static final String MARKDOWN_FILE_NAME = "stackmate-benchmark-report.md";

    private final ObjectMapper mapper =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public GeneratedReports write(
            Path targetDirectory,
            LocalModelBenchmarkHarness.BenchmarkResult modelResult,
            ModelDescriptor model)
            throws IOException {
        Files.createDirectories(targetDirectory);
        EvaluationSummary deterministic = new DeterministicEvaluationHarness().evaluate();
        Path json = targetDirectory.resolve(JSON_FILE_NAME);
        Path markdown = targetDirectory.resolve(MARKDOWN_FILE_NAME);
        mapper.writeValue(json.toFile(), report(modelResult, deterministic, model));
        Files.writeString(
                markdown, markdown(modelResult, deterministic, model), StandardCharsets.UTF_8);
        return new GeneratedReports(json, markdown);
    }

    private Map<String, Object> report(
            LocalModelBenchmarkHarness.BenchmarkResult modelResult,
            EvaluationSummary deterministic,
            ModelDescriptor model) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", "2");
        result.put("generatedAt", Instant.now().toString());
        result.put("corpusVersion", BenchmarkCorpus.CORPUS_VERSION);
        result.put("promptVersion", LocalModelBenchmarkHarness.PROMPT_VERSION);
        result.put("language", modelResult.language());
        result.put("evaluatedCaseCount", modelResult.raw().caseCount());
        result.put("machine", machine());
        result.put(
                "inference",
                Map.of(
                        "engine",
                        "Jlama",
                        "engineVersion",
                        "0.8.4",
                        "deployment",
                        "managed-child-jvm-stdio",
                        "model",
                        model,
                        "runtimeNetworkCalls",
                        0,
                        "modelWeightsBundled",
                        false));
        Map<String, Object> modes = new LinkedHashMap<>();
        modes.put(LocalModelBenchmarkHarness.RAW_MODE, modelResult.raw());
        modes.put(LocalModelBenchmarkHarness.STRUCTURED_MODE, modelResult.structured());
        modes.put(
                "C_DETERMINISTIC",
                Map.of(
                        "status", "EXECUTED",
                        "promptVersion", "NOT_APPLICABLE_DETERMINISTIC",
                        "metrics", deterministic));
        result.put("modes", modes);
        result.put(
                "measurementLimits",
                Map.of(
                        "memory",
                        "NOT_MEASURED: portable process-tree RSS requires an OS-specific sampler",
                        "modeARawness",
                        "Raw sanitized stack text plus a global category taxonomy and source "
                                + "allow-list; no case-specific cause/check answer is supplied",
                        "modeASelections",
                        "Cause/check accuracy is N/A because case-specific cause/check IDs are deliberately withheld",
                        "modelFactExtraction",
                        "N/A: the model output contract selects supplied fact IDs; deterministic "
                                + "Mode C owns fact extraction",
                        "unparsedOutputSafety",
                        "Rejected raw engine text is intentionally not exposed by the production "
                                + "SPI; content-level leakage counts cover parsed output candidates",
                        "languageQuality",
                        "Manual review is required; see manual-review-worksheet.md"));
        return result;
    }

    private Map<String, Object> machine() {
        Map<String, Object> machine = new LinkedHashMap<>();
        machine.put("osName", System.getProperty("os.name"));
        machine.put("osVersion", System.getProperty("os.version"));
        machine.put("osArchitecture", System.getProperty("os.arch"));
        machine.put("javaVersion", System.getProperty("java.version"));
        machine.put("javaVendor", System.getProperty("java.vendor"));
        machine.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        machine.put("maximumJvmHeapBytes", Runtime.getRuntime().maxMemory());
        return machine;
    }

    private String markdown(
            LocalModelBenchmarkHarness.BenchmarkResult modelResult,
            EvaluationSummary deterministic,
            ModelDescriptor model) {
        StringBuilder text = new StringBuilder();
        text.append("# StackMate local-model benchmark report\n\n");
        text.append("Corpus: `")
                .append(BenchmarkCorpus.CORPUS_VERSION)
                .append("` (")
                .append(modelResult.raw().caseCount())
                .append(" of 50 controlled cases) · language: `")
                .append(modelResult.language())
                .append("`\n\n");
        text.append("Model: `")
                .append(model.displayName())
                .append("` · engine: `Jlama 0.8.4` · prompt: `")
                .append(LocalModelBenchmarkHarness.PROMPT_VERSION)
                .append("`\n\n");
        text.append("Mode A receives raw sanitized bounded stack text, the same global category ")
                .append(
                        "taxonomy for every case, and a source allow-list used only by output validation. ")
                .append("It receives no case-specific cause or check IDs.\n\n");
        mode(text, "Mode A - raw model", modelResult.raw(), false);
        mode(text, "Mode B - structured model", modelResult.structured(), true);
        text.append("## Mode C - deterministic\n\n");
        metric(text, "Category accuracy", deterministic.categoryAccuracy().rate());
        metric(text, "Root-type accuracy", deterministic.rootTypeAccuracy().rate());
        metric(text, "Fact exact accuracy", deterministic.factExactAccuracy().rate());
        metric(text, "Cause selection accuracy", deterministic.causeAccuracy().rate());
        metric(text, "Check selection accuracy", deterministic.checkAccuracy().rate());
        text.append("\nSafety counts: fabricated sources `")
                .append(deterministic.fabricatedSourceReferenceCount())
                .append("`, unknown IDs `")
                .append(deterministic.unknownIdCount())
                .append("`, secret leakage `")
                .append(deterministic.secretLeakageCount())
                .append("`, prompt-injection failures `")
                .append(deterministic.promptInjectionFailureCount())
                .append("`.\n\n");
        text.append("## Measurement limits\n\n");
        text.append("- Process-tree RSS was not measured by this portable Java harness.\n");
        text.append("- Mode A case-specific cause and check selection are intentionally N/A.\n");
        text.append(
                "- Category selection is retained only for the raw-stack research baseline; "
                        + "structured mode receives a trusted deterministic category.\n");
        text.append(
                "- Root-type accuracy is N/A because root exception type is deterministic-owned.\n");
        text.append(
                "- Content-level leakage counts cover parsed candidates; the production SPI "
                        + "intentionally discards rejected raw engine text.\n");
        text.append("- English/Turkish readability requires `manual-review-worksheet.md`.\n");
        return text.toString();
    }

    private void mode(
            StringBuilder text, String title, ModelModeSummary summary, boolean selectionsApply) {
        text.append("## ").append(title).append("\n\n");
        if (!"EXECUTED".equals(summary.status())) {
            text.append("- Status: NOT EXECUTED (disabled for structured-only screening)\n\n");
            return;
        }
        text.append("- Valid outputs: ")
                .append(summary.validOutputCount())
                .append('/')
                .append(summary.caseCount())
                .append('\n');
        text.append("- Invalid outputs: ").append(summary.invalidOutputCount()).append('\n');
        text.append("- Engine failures: ").append(summary.engineFailureCount()).append('\n');
        if (selectionsApply) {
            text.append("- Category accuracy: N/A (deterministic-owned)\n");
        } else {
            metric(text, "Research category selection", rate(summary.categoryAccuracy()));
        }
        text.append("- Root-type accuracy: N/A (deterministic-owned)\n");
        text.append("- Fact extraction accuracy: N/A (the model selects supplied evidence IDs)\n");
        if (selectionsApply) {
            metric(text, "Cause selection accuracy", rate(summary.causeSelectionAccuracy()));
            metric(text, "Check selection accuracy", rate(summary.checkSelectionAccuracy()));
        } else {
            text.append("- Cause selection accuracy: N/A\n");
            text.append("- Check selection accuracy: N/A\n");
        }
        text.append("- Latency median / P95: ")
                .append(summary.latencyMilliseconds().get("median"))
                .append(" ms / ")
                .append(summary.latencyMilliseconds().get("p95"))
                .append(" ms\n");
        text.append("- Input / output characters: ")
                .append(summary.totalInputCharacters())
                .append(" / ")
                .append(summary.totalOutputCharacters())
                .append("\n\n");
        text.append("Safety counts: unknown IDs `")
                .append(summary.unknownIdCount())
                .append("`, fabricated sources `")
                .append(summary.fabricatedSourceReferenceCount())
                .append("`, forbidden claims `")
                .append(summary.forbiddenClaimCount())
                .append("`, secret leakage `")
                .append(summary.secretLeakageCount())
                .append("`, prompt-injection failures `")
                .append(summary.promptInjectionFailureCount())
                .append("`.\n\n");
    }

    private static double rate(Map<String, Object> score) {
        Object value = score.get("rate");
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static void metric(StringBuilder text, String label, double rate) {
        text.append("- ")
                .append(label)
                .append(": ")
                .append(String.format(Locale.ROOT, "%.2f%%", rate * 100.0))
                .append('\n');
    }

    public record GeneratedReports(Path json, Path markdown) {}
}
