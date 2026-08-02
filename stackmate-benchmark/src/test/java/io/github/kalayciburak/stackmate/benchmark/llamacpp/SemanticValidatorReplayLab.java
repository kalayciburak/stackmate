package io.github.kalayciburak.stackmate.benchmark.llamacpp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.benchmark.corpus.qualification.QualificationBlindCase;
import io.github.kalayciburak.stackmate.benchmark.corpus.qualification.QualificationBlindIncidentFactory;
import io.github.kalayciburak.stackmate.benchmark.corpus.qualification.QualificationBlindManifest;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Case;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2IncidentFactory;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Manifest;
import io.github.kalayciburak.stackmate.benchmark.prompt.ExperimentalClaimGroundingValidator;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.inference.DefaultInferenceOutputValidator;
import io.github.kalayciburak.stackmate.inference.DeterministicInferenceRequestFactory;
import io.github.kalayciburak.stackmate.inference.OutputValidationIssue;
import io.github.kalayciburak.stackmate.inference.OutputValidationResult;
import io.github.kalayciburak.stackmate.inference.StructuredInferenceOutput;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Explicit deterministic replay of captured model outputs through the current validator. */
class SemanticValidatorReplayLab {

    private final ObjectMapper mapper =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final DeterministicDiagnosticEngine diagnosticEngine =
            new DeterministicDiagnosticEngine(
                    DefaultDiagnosticAnalyzers.aggregator(), new IncidentFingerprinter());
    private final DeterministicInferenceRequestFactory requestFactory =
            new DeterministicInferenceRequestFactory(DefaultSanitizer.defaults());
    private final DefaultInferenceOutputValidator validator =
            new DefaultInferenceOutputValidator(DefaultSanitizer.defaults());
    private final ExperimentalClaimGroundingValidator claimValidator =
            new ExperimentalClaimGroundingValidator();

    @Test
    void replaysCapturedOutputsWithoutModelExecution() throws Exception {
        String configured = System.getProperty("stackmate.validator-replay.reports", "");
        if (configured.isBlank()) {
            throw new IllegalArgumentException(
                    "stackmate.validator-replay.reports must list captured JSON reports");
        }
        Map<String, IncidentSnapshot> incidents = incidents();
        List<ReplayResult> results = new ArrayList<>();
        for (String value : configured.split(";")) {
            Path reportPath = Path.of(value.strip()).toAbsolutePath().normalize();
            JsonNode report = mapper.readTree(Files.readString(reportPath, StandardCharsets.UTF_8));
            for (JsonNode captured : report.path("results")) {
                if (!captured.path("valid").asBoolean()) {
                    continue;
                }
                String caseId = captured.path("caseId").asText();
                IncidentSnapshot incident = incidents.get(caseId);
                if (incident == null) {
                    throw new IllegalStateException(
                            "captured report contains unknown case: " + caseId);
                }
                var request =
                        requestFactory.create(diagnosticEngine.analyze(incident), 16_000, 8_192);
                StructuredInferenceOutput output = capturedOutput(captured);
                OutputValidationResult validation = validator.validate(request, output);
                List<String> codes =
                        validation instanceof OutputValidationResult.Invalid invalid
                                ? invalid.issues().stream()
                                        .map(OutputValidationIssue::code)
                                        .map(Enum::name)
                                        .distinct()
                                        .toList()
                                : List.of();
                if (claimValidator.hasUnsupportedClaim(request, output)) {
                    List<String> combined = new ArrayList<>(codes);
                    combined.add("UNSUPPORTED_CLAIM_EXPERIMENTAL");
                    codes = combined.stream().distinct().toList();
                }
                results.add(
                        new ReplayResult(
                                reportPath.getFileName().toString(),
                                captured.path("promptId").asText("P0_BASELINE"),
                                captured.path("dataset").asText(),
                                caseId,
                                codes.isEmpty(),
                                codes));
            }
        }

        ReplayReport report =
                new ReplayReport("phase-5-1-validator-replay-v1", summaries(results), results);
        Path output =
                Path.of(
                                System.getProperty(
                                        "stackmate.validator-replay.output",
                                        "target/phase-5-1-validator-replay.json"))
                        .toAbsolutePath()
                        .normalize();
        Files.createDirectories(output.getParent());
        Files.writeString(
                output,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report)
                        + System.lineSeparator(),
                StandardCharsets.UTF_8);

        assertThat(results).isNotEmpty();
    }

    private Map<String, IncidentSnapshot> incidents() throws Exception {
        Map<String, IncidentSnapshot> incidents = new LinkedHashMap<>();
        CorpusV2IncidentFactory v2 = new CorpusV2IncidentFactory();
        for (CorpusV2Case benchmarkCase : CorpusV2Manifest.load().cases()) {
            incidents.put(benchmarkCase.caseId(), v2.create(benchmarkCase));
        }
        QualificationBlindIncidentFactory blind = new QualificationBlindIncidentFactory();
        for (QualificationBlindCase benchmarkCase : QualificationBlindManifest.load().cases()) {
            incidents.put(benchmarkCase.caseId(), blind.create(benchmarkCase));
        }
        return Map.copyOf(incidents);
    }

    private StructuredInferenceOutput capturedOutput(JsonNode captured) {
        return new StructuredInferenceOutput(
                "2",
                captured.path("summary").asText(),
                captured.path("explanation").asText(),
                strings(captured.path("selectedCauseIds")),
                strings(captured.path("selectedCheckIds")),
                strings(captured.path("usedEvidenceIds")),
                strings(captured.path("limitations")));
    }

    private List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    private List<ReplaySummary> summaries(List<ReplayResult> results) {
        Map<String, List<ReplayResult>> groups = new LinkedHashMap<>();
        results.forEach(
                result ->
                        groups.computeIfAbsent(
                                        result.report() + ":" + result.promptId(),
                                        ignored -> new ArrayList<>())
                                .add(result));
        return groups.entrySet().stream()
                .map(
                        entry ->
                                new ReplaySummary(
                                        entry.getKey(),
                                        entry.getValue().size(),
                                        (int)
                                                entry.getValue().stream()
                                                        .filter(ReplayResult::accepted)
                                                        .count(),
                                        entry.getValue().stream()
                                                .filter(result -> !result.accepted())
                                                .flatMap(result -> result.issueCodes().stream())
                                                .collect(
                                                        java.util.stream.Collectors.groupingBy(
                                                                value -> value,
                                                                LinkedHashMap::new,
                                                                java.util.stream.Collectors
                                                                        .counting()))))
                .toList();
    }

    private record ReplayResult(
            String report,
            String promptId,
            String dataset,
            String caseId,
            boolean accepted,
            List<String> issueCodes) {}

    private record ReplaySummary(
            String group, int capturedValidOutputs, int accepted, Map<String, Long> rejections) {}

    private record ReplayReport(
            String schema, List<ReplaySummary> summaries, List<ReplayResult> results) {}
}
