package io.github.kalayciburak.stackmate.benchmark.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.kalayciburak.stackmate.benchmark.corpus.BenchmarkCorpus;
import io.github.kalayciburak.stackmate.benchmark.corpus.BenchmarkDomain;
import io.github.kalayciburak.stackmate.benchmark.corpus.GroundTruthCase;
import io.github.kalayciburak.stackmate.benchmark.evaluation.DeterministicEvaluationHarness;
import io.github.kalayciburak.stackmate.benchmark.evaluation.EvaluationSummary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Writes the machine-readable and human-readable Phase 5 benchmark reports. */
public final class BenchmarkReportGenerator {

    public static final String JSON_FILE_NAME = "stackmate-benchmark-report.json";
    public static final String MARKDOWN_FILE_NAME = "stackmate-benchmark-report.md";

    private final DeterministicEvaluationHarness harness;
    private final ObjectMapper objectMapper;

    public BenchmarkReportGenerator() {
        this(new DeterministicEvaluationHarness());
    }

    BenchmarkReportGenerator(DeterministicEvaluationHarness harness) {
        this.harness = harness;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public GeneratedReports writeReports(Path targetDirectory) throws IOException {
        Files.createDirectories(targetDirectory);
        EvaluationSummary summary = harness.evaluate();
        Path json = targetDirectory.resolve(JSON_FILE_NAME);
        Path markdown = targetDirectory.resolve(MARKDOWN_FILE_NAME);
        objectMapper.writeValue(json.toFile(), report(summary));
        Files.writeString(markdown, markdown(summary), StandardCharsets.UTF_8);
        return new GeneratedReports(json, markdown, summary);
    }

    private Map<String, Object> report(EvaluationSummary summary) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", "1");
        report.put("corpusVersion", BenchmarkCorpus.CORPUS_VERSION);
        report.put("caseDistribution", distribution());
        report.put("machine", observedMachine());
        Map<String, Object> modes = new LinkedHashMap<>();
        modes.put("A_RAW_MODEL", notExecutedModelMode());
        modes.put("B_STRUCTURED_MODEL", notExecutedModelMode());
        modes.put(
                "C_DETERMINISTIC",
                Map.of(
                        "status", "EXECUTED",
                        "reason", "DETERMINISTIC_BASELINE",
                        "promptVersion", "NOT_APPLICABLE_DETERMINISTIC",
                        "metrics", summary));
        report.put("modes", modes);
        report.put(
                "measurementNotice",
                "No latency, memory, throughput, engine, quantization, or model measurements were executed.");
        report.put("manualReviewWorksheet", "classpath:manual-review-worksheet.md");
        return report;
    }

    private Map<String, Object> notExecutedModelMode() {
        return Map.of(
                "status", "NOT_EXECUTED",
                "reason", "NO_MODEL",
                "promptVersion", "NOT_APPLICABLE_NO_MODEL");
    }

    private Map<String, Long> distribution() {
        return BenchmarkCorpus.cases().stream()
                .collect(
                        Collectors.groupingBy(
                                item -> item.domain().name(),
                                LinkedHashMap::new,
                                Collectors.counting()));
    }

    private Map<String, Object> observedMachine() {
        Map<String, Object> machine = new LinkedHashMap<>();
        machine.put("osName", System.getProperty("os.name"));
        machine.put("osVersion", System.getProperty("os.version"));
        machine.put("osArchitecture", System.getProperty("os.arch"));
        machine.put("javaVersion", System.getProperty("java.version"));
        machine.put("javaVendor", System.getProperty("java.vendor"));
        machine.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        return machine;
    }

    private String markdown(EvaluationSummary summary) {
        StringBuilder text = new StringBuilder();
        text.append("# StackMate Phase 5 benchmark report\n\n");
        text.append("Corpus: `").append(BenchmarkCorpus.CORPUS_VERSION).append("` (50 cases)\n\n");
        text.append("## Execution modes\n\n");
        text.append("| Mode | Status | Reason | Prompt version |\n");
        text.append("| --- | --- | --- | --- |\n");
        text.append("| A - raw model | NOT_EXECUTED | NO_MODEL | NOT_APPLICABLE_NO_MODEL |\n");
        text.append(
                "| B - structured model | NOT_EXECUTED | NO_MODEL | NOT_APPLICABLE_NO_MODEL |\n");
        text.append(
                "| C - deterministic | EXECUTED | DETERMINISTIC_BASELINE | NOT_APPLICABLE_DETERMINISTIC |\n\n");
        text.append(
                "No latency, memory, throughput, engine, quantization, or model measurements were executed.\n\n");
        text.append("## Mode C actual metrics\n\n");
        metric(text, "Category accuracy", summary.categoryAccuracy().rate());
        metric(text, "Root-type accuracy", summary.rootTypeAccuracy().rate());
        metric(text, "Fact exact accuracy", summary.factExactAccuracy().rate());
        metric(text, "Fact precision", summary.factPrecision().rate());
        metric(text, "Fact recall", summary.factRecall().rate());
        metric(text, "Cause selection accuracy", summary.causeAccuracy().rate());
        metric(text, "Check selection accuracy", summary.checkAccuracy().rate());
        text.append("\n| Safety validation | Count |\n");
        text.append("| --- | ---: |\n");
        row(text, "Fabricated source references", summary.fabricatedSourceReferenceCount());
        row(text, "Unknown IDs", summary.unknownIdCount());
        row(text, "Forbidden claims", summary.forbiddenClaimCount());
        row(text, "Secret leakage", summary.secretLeakageCount());
        row(text, "Prompt-injection failures", summary.promptInjectionFailureCount());
        row(text, "Input characters", summary.totalInputCharacters());
        row(text, "Output characters", summary.totalOutputCharacters());
        text.append("\n## Corpus distribution\n\n");
        for (BenchmarkDomain domain : BenchmarkDomain.values()) {
            long count =
                    BenchmarkCorpus.cases().stream()
                            .map(GroundTruthCase::domain)
                            .filter(domain::equals)
                            .count();
            text.append("- ").append(domain).append(": ").append(count).append('\n');
        }
        text.append("\nManual English review uses `manual-review-worksheet.md`.\n");
        return text.toString();
    }

    private void metric(StringBuilder text, String label, double rate) {
        text.append("- ")
                .append(label)
                .append(": ")
                .append(String.format(java.util.Locale.ROOT, "%.2f%%", rate * 100.0))
                .append('\n');
    }

    private void row(StringBuilder text, String label, int value) {
        text.append("| ").append(label).append(" | ").append(value).append(" |\n");
    }

    public record GeneratedReports(Path json, Path markdown, EvaluationSummary summary) {}
}
