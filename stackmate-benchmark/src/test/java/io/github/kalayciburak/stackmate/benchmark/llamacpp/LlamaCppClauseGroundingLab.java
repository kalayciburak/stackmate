package io.github.kalayciburak.stackmate.benchmark.llamacpp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Case;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2IncidentFactory;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Manifest;
import io.github.kalayciburak.stackmate.benchmark.prompt.ExperimentalClaimGroundingValidator;
import io.github.kalayciburak.stackmate.benchmark.prompt.PromptAblationManifest;
import io.github.kalayciburak.stackmate.benchmark.prompt.PromptVariantManifest;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.inference.DefaultInferenceOutputValidator;
import io.github.kalayciburak.stackmate.inference.DeterministicInferenceRequestFactory;
import io.github.kalayciburak.stackmate.inference.InferenceCauseCandidate;
import io.github.kalayciburak.stackmate.inference.InferenceCheckCandidate;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.inference.OutputValidationResult;
import io.github.kalayciburak.stackmate.inference.StructuredInferenceOutput;
import io.github.kalayciburak.stackmate.inference.jvm.PromptRendererTestAccess;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Explicit clause-grounding schema experiment; excluded from normal test discovery. */
class LlamaCppClauseGroundingLab {

    private static final String MODEL_SHA256 =
            "57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8";
    private static final String EXECUTABLE_SHA256 =
            "4208fb923f6109143d47035aee315295c1c1ce87a062a8cfbbcb02a4d8a3fee2";
    private static final String SELECTED_PROMPT_SHA256 =
            "d5117da41bd8af64982035fb75dbac91968249ea48fd02f120c532755074bf7f";
    private static final Pattern PROMPT_EVAL_TIME =
            Pattern.compile("(?im)prompt eval time\\s*=\\s*([0-9.]+)\\s*ms");
    private static final Pattern EVAL_TIME =
            Pattern.compile("(?im)(?<!prompt )eval time\\s*=\\s*([0-9.]+)\\s*ms");
    private static final String FIELD_EXAMPLE =
            "{\"schemaVersion\":\"2\",\"summary\":\"The supplied evidence is insufficient.\",\"explanation\":\"No supplied cause has direct support.\",\"selectedCauseIds\":[],\"selectedCheckIds\":[],\"usedEvidenceIds\":[],\"limitations\":[\"EVIDENCE_INSUFFICIENT\"]}";
    private static final String CLAUSE_EXAMPLE =
            "{\"schemaVersion\":\"2\",\"summary\":{\"text\":\"The supplied evidence is insufficient.\",\"evidenceIds\":[\"F1\"]},\"explanation\":{\"text\":\"No supplied cause has direct support.\",\"evidenceIds\":[\"F1\"]},\"selectedCauseIds\":[],\"selectedCheckIds\":[],\"limitations\":[\"EVIDENCE_INSUFFICIENT\"]}";
    private static final String FIELD_SCHEMA =
            "{\"schemaVersion\":\"2\",\"summary\":\"concise string\",\"explanation\":\"concise string\",\"selectedCauseIds\":[\"supplied id\"],\"selectedCheckIds\":[\"supplied id\"],\"usedEvidenceIds\":[\"supplied id\"],\"limitations\":[]}";
    private static final String CLAUSE_SCHEMA =
            "{\"schemaVersion\":\"2\",\"summary\":{\"text\":\"concise string\",\"evidenceIds\":[\"supplied fact id\"]},\"explanation\":{\"text\":\"concise string\",\"evidenceIds\":[\"supplied fact id\"]},\"selectedCauseIds\":[\"supplied id\"],\"selectedCheckIds\":[\"supplied id\"],\"limitations\":[]}";

    private final ObjectMapper mapper =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
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
    void comparesClauseLevelGroundingOnSelectionValidation() throws Exception {
        Configuration configuration = Configuration.fromSystemProperties();
        verifyArtifacts(configuration);
        PromptVariantManifest.PromptVariant selected = PromptVariantManifest.load().require("P3");
        assertThat(selected.sha256()).isEqualTo(SELECTED_PROMPT_SHA256);
        String clausePrompt = clausePrompt(PromptVariantManifest.load().prompt("P3"));
        List<ClauseResult> results = new ArrayList<>();
        Instant started = Instant.now();

        for (QualificationInput input : inputs()) {
            ClauseResult result = execute(configuration, clausePrompt, input);
            results.add(result);
            System.out.printf(
                    Locale.ROOT,
                    "[Phase5.1 clause] %s valid=%s cause=%s grounded=%s processMs=%d%n",
                    input.caseId(),
                    result.valid(),
                    result.causeCorrect(),
                    result.clauseGroundingValid(),
                    result.processMillis());
        }

        ClauseSummary summary = summarize(results);
        ClauseReport report =
                new ClauseReport(
                        "stackmate-phase-5-1-clause-grounding-v1",
                        "SELECTION_VALIDATION",
                        selected.id(),
                        selected.version(),
                        selected.sha256(),
                        sha256(clausePrompt),
                        clausePrompt.length(),
                        (int) Math.ceil(clausePrompt.length() / 4.0),
                        MODEL_SHA256,
                        EXECUTABLE_SHA256,
                        configuration.contextSize(),
                        configuration.maximumOutputTokens(),
                        42,
                        "GREEDY",
                        started.toString(),
                        Instant.now().toString(),
                        summary,
                        results);
        Files.createDirectories(configuration.outputDirectory());
        Files.writeString(
                configuration.outputDirectory().resolve("phase-5-1-clause-grounding.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report)
                        + System.lineSeparator(),
                StandardCharsets.UTF_8);

        assertThat(results).hasSize(20).allMatch(ClauseResult::fallbackSucceeded);
    }

    private String clausePrompt(String fieldPrompt) {
        String converted =
                fieldPrompt
                        .replace(FIELD_EXAMPLE, CLAUSE_EXAMPLE)
                        .replace(
                                "List only supplied evidence IDs actually used.",
                                "Attach supplied evidence IDs to each generated clause.")
                        .replace(FIELD_SCHEMA, CLAUSE_SCHEMA);
        if (converted.equals(fieldPrompt)
                || converted.contains(FIELD_EXAMPLE)
                || converted.contains(FIELD_SCHEMA)) {
            throw new IllegalStateException(
                    "selected prompt cannot be converted deterministically");
        }
        return converted;
    }

    private ClauseResult execute(
            Configuration configuration, String systemPrompt, QualificationInput input)
            throws Exception {
        InferenceRequest request =
                requestFactory.create(diagnosticEngine.analyze(input.incident()), 16_000, 8_192);
        List<String> expectedCauses =
                expected(
                        input.abstentionRequired(),
                        input.expectedCauseCount(),
                        request.candidateCauses(),
                        InferenceCauseCandidate::id);
        List<String> expectedChecks =
                expected(
                        input.abstentionRequired(),
                        input.expectedCheckCount(),
                        request.candidateChecks(),
                        InferenceCheckCandidate::id);
        PromptRendererTestAccess.RenderedPrompt production =
                PromptRendererTestAccess.initial(request);
        LaunchResult launch =
                launch(
                        configuration,
                        request,
                        new PromptRendererTestAccess.RenderedPrompt(
                                systemPrompt, production.user()),
                        input.caseId());
        ClauseOutput output = null;
        List<String> issues = new ArrayList<>();
        if (launch.timedOut()) {
            issues.add("ENGINE_TIMEOUT");
        } else if (launch.exitCode() != 0) {
            issues.add("ENGINE_EXIT");
        } else if (launch.hiddenThinking()) {
            issues.add("HIDDEN_THINKING");
        } else {
            try {
                output = mapper.readValue(launch.output(), ClauseOutput.class);
                validateClauseOutput(request, output, issues);
            } catch (Exception failure) {
                issues.add("SCHEMA_OR_JSON");
            }
        }
        boolean valid = issues.isEmpty();
        Set<String> usedEvidence = output == null ? Set.of() : usedEvidence(output);
        StructuredInferenceOutput adapted = output == null ? null : adapt(output, usedEvidence);
        return new ClauseResult(
                input.caseId(),
                valid,
                output != null
                        && Set.copyOf(output.selectedCauseIds()).equals(Set.copyOf(expectedCauses)),
                output != null
                        && Set.copyOf(output.selectedCheckIds()).equals(Set.copyOf(expectedChecks)),
                output != null && clauseGroundingValid(request, output, usedEvidence),
                adapted != null && claimValidator.hasUnsupportedClaim(request, adapted),
                !valid || output != null,
                launch.outputSha256(),
                launch.output().length(),
                launch.processMillis(),
                launch.promptEvalMillis(),
                launch.generationMillis(),
                output == null ? "" : output.summary().text(),
                output == null ? List.of() : output.summary().evidenceIds(),
                output == null ? "" : output.explanation().text(),
                output == null ? List.of() : output.explanation().evidenceIds(),
                output == null ? List.of() : output.selectedCauseIds(),
                output == null ? List.of() : output.selectedCheckIds(),
                output == null ? List.of() : output.limitations(),
                List.copyOf(issues));
    }

    private void validateClauseOutput(
            InferenceRequest request, ClauseOutput output, List<String> issues) {
        if (!"2".equals(output.schemaVersion())) {
            issues.add("UNKNOWN_SCHEMA_VERSION");
            return;
        }
        Set<String> knownEvidence =
                request.facts().stream()
                        .map(item -> item.id())
                        .collect(java.util.stream.Collectors.toSet());
        Set<String> usedEvidence = usedEvidence(output);
        if (!knownEvidence.containsAll(usedEvidence)) {
            issues.add("UNKNOWN_FACT_ID");
        }
        if (!clauseGroundingValid(request, output, usedEvidence)) {
            issues.add("CLAUSE_GROUNDING_INVALID");
        }
        StructuredInferenceOutput adapted = adapt(output, usedEvidence);
        OutputValidationResult validation = validator.validate(request, adapted);
        if (validation instanceof OutputValidationResult.Invalid invalid) {
            invalid.issues().forEach(issue -> issues.add(issue.code().name()));
        }
    }

    private boolean clauseGroundingValid(
            InferenceRequest request, ClauseOutput output, Set<String> usedEvidence) {
        if (output.summary().text().isBlank()
                || output.explanation().text().isBlank()
                || output.summary().evidenceIds().isEmpty()
                || output.explanation().evidenceIds().isEmpty()) {
            return false;
        }
        return request.candidateCauses().stream()
                        .filter(candidate -> output.selectedCauseIds().contains(candidate.id()))
                        .allMatch(
                                candidate ->
                                        candidate.evidenceIds().stream()
                                                .anyMatch(usedEvidence::contains))
                && request.candidateChecks().stream()
                        .filter(candidate -> output.selectedCheckIds().contains(candidate.id()))
                        .allMatch(
                                candidate ->
                                        candidate.evidenceIds().stream()
                                                .anyMatch(usedEvidence::contains));
    }

    private Set<String> usedEvidence(ClauseOutput output) {
        Set<String> used = new HashSet<>(output.summary().evidenceIds());
        used.addAll(output.explanation().evidenceIds());
        return Set.copyOf(used);
    }

    private StructuredInferenceOutput adapt(ClauseOutput output, Set<String> usedEvidence) {
        return new StructuredInferenceOutput(
                output.schemaVersion(),
                output.summary().text(),
                output.explanation().text(),
                output.selectedCauseIds(),
                output.selectedCheckIds(),
                usedEvidence.stream().sorted().toList(),
                output.limitations());
    }

    private <T> List<String> expected(
            boolean abstention,
            int count,
            List<T> candidates,
            java.util.function.Function<T, String> id) {
        return abstention ? List.of() : candidates.stream().limit(count).map(id).toList();
    }

    private List<QualificationInput> inputs() throws Exception {
        Set<String> selected =
                Set.copyOf(
                        PromptAblationManifest.load()
                                .caseIds(PromptAblationManifest.Dataset.SELECTION_VALIDATION));
        Set<String> restraint =
                Set.copyOf(PromptAblationManifest.load().abstentionRequiredCaseIds());
        CorpusV2IncidentFactory incidents = new CorpusV2IncidentFactory();
        Map<String, QualificationInput> byId = new LinkedHashMap<>();
        for (CorpusV2Case benchmarkCase : CorpusV2Manifest.load().cases()) {
            if (selected.contains(benchmarkCase.caseId())) {
                byId.put(
                        benchmarkCase.caseId(),
                        new QualificationInput(
                                benchmarkCase.caseId(),
                                restraint.contains(benchmarkCase.caseId()),
                                benchmarkCase.expectedCauseKeys().size(),
                                benchmarkCase.expectedCheckKeys().size(),
                                incidents.create(benchmarkCase)));
            }
        }
        return PromptAblationManifest.load()
                .caseIds(PromptAblationManifest.Dataset.SELECTION_VALIDATION)
                .stream()
                .map(byId::get)
                .toList();
    }

    private LaunchResult launch(
            Configuration configuration,
            InferenceRequest request,
            PromptRendererTestAccess.RenderedPrompt prompt,
            String caseId)
            throws Exception {
        Path runDirectory =
                Files.createTempDirectory(
                        configuration.outputDirectory(),
                        "clause-" + caseId.toLowerCase(Locale.ROOT) + '-');
        try {
            Path promptFile = runDirectory.resolve("prompt.txt");
            Path schemaFile = runDirectory.resolve("schema.json");
            Path outputFile = runDirectory.resolve("output.json");
            Path logFile = runDirectory.resolve("runtime.log");
            Files.writeString(promptFile, renderPrompt(prompt), StandardCharsets.UTF_8);
            Files.writeString(
                    schemaFile, mapper.writeValueAsString(schema(request)), StandardCharsets.UTF_8);
            List<String> command =
                    List.of(
                            configuration.executable().toString(),
                            "--model",
                            configuration.model().toString(),
                            "--ctx-size",
                            Integer.toString(configuration.contextSize()),
                            "--predict",
                            Integer.toString(configuration.maximumOutputTokens()),
                            "--threads",
                            Integer.toString(configuration.threads()),
                            "--threads-batch",
                            Integer.toString(configuration.threads()),
                            "--seed",
                            "42",
                            "--reasoning",
                            "off",
                            "--no-conversation",
                            "--no-display-prompt",
                            "--file",
                            promptFile.toString(),
                            "--json-schema-file",
                            schemaFile.toString(),
                            "--temperature",
                            "0",
                            "--top-k",
                            "1",
                            "--top-p",
                            "1");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(runDirectory.toFile());
            builder.redirectOutput(outputFile.toFile());
            builder.redirectError(logFile.toFile());
            reduceEnvironment(builder.environment());
            long started = System.nanoTime();
            Process process = builder.start();
            boolean finished =
                    process.waitFor(configuration.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                destroyProcessTree(process);
                process.waitFor(5, TimeUnit.SECONDS);
            }
            long processMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            String output =
                    Files.exists(outputFile)
                            ? modelOutput(Files.readString(outputFile, StandardCharsets.UTF_8))
                            : "";
            String log =
                    Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
            return new LaunchResult(
                    output,
                    sha256(output),
                    finished ? process.exitValue() : -1,
                    !finished,
                    containsThinking(output),
                    processMillis,
                    timing(PROMPT_EVAL_TIME, log),
                    timing(EVAL_TIME, log));
        } finally {
            deleteRunDirectory(runDirectory);
        }
    }

    private ObjectNode schema(InferenceRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        ObjectNode properties = root.putObject("properties");
        properties.set("schemaVersion", enumString(List.of("2")));
        properties.set("summary", clauseSchema(request));
        properties.set("explanation", clauseSchema(request));
        properties.set(
                "selectedCauseIds",
                idArray(request.candidateCauses().stream().map(item -> item.id()).toList()));
        properties.set(
                "selectedCheckIds",
                idArray(request.candidateChecks().stream().map(item -> item.id()).toList()));
        ObjectNode limitations = mapper.createObjectNode();
        limitations.put("type", "array");
        limitations.put("maxItems", 1);
        limitations.set("items", boundedString(1, 96));
        properties.set("limitations", limitations);
        ArrayNode required = root.putArray("required");
        List.of(
                        "schemaVersion",
                        "summary",
                        "explanation",
                        "selectedCauseIds",
                        "selectedCheckIds",
                        "limitations")
                .forEach(required::add);
        return root;
    }

    private ObjectNode clauseSchema(InferenceRequest request) {
        ObjectNode clause = mapper.createObjectNode();
        clause.put("type", "object");
        clause.put("additionalProperties", false);
        ObjectNode properties = clause.putObject("properties");
        properties.set("text", boundedString(1, 180));
        properties.set(
                "evidenceIds", idArray(request.facts().stream().map(item -> item.id()).toList()));
        ArrayNode required = clause.putArray("required");
        required.add("text");
        required.add("evidenceIds");
        return clause;
    }

    private ObjectNode boundedString(int minimum, int maximum) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "string");
        node.put("minLength", minimum);
        node.put("maxLength", maximum);
        return node;
    }

    private ObjectNode enumString(List<String> values) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "string");
        ArrayNode allowed = node.putArray("enum");
        values.forEach(allowed::add);
        return node;
    }

    private ObjectNode idArray(List<String> ids) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "array");
        node.put("maxItems", ids.size());
        node.set("items", enumString(ids));
        return node;
    }

    private String renderPrompt(PromptRendererTestAccess.RenderedPrompt prompt) {
        String bounds =
                "Output bounds are mandatory: make summary text one complete sentence of at most "
                        + "10 words and explanation text one complete sentence of at most 20 words. "
                        + "Cite at least one supplied evidence ID for each text field. Select only "
                        + "the most specific supplied cause and check IDs.";
        return "<|im_start|>system\n"
                + prompt.system()
                + "\n\n"
                + bounds
                + "<|im_end|>\n<|im_start|>user\n"
                + prompt.user()
                + "<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n";
    }

    private ClauseSummary summarize(List<ClauseResult> results) {
        List<Long> process = results.stream().map(ClauseResult::processMillis).sorted().toList();
        List<Double> prompt =
                results.stream().map(ClauseResult::promptEvalMillis).sorted().toList();
        List<Double> generation =
                results.stream().map(ClauseResult::generationMillis).sorted().toList();
        return new ClauseSummary(
                results.size(),
                count(results, ClauseResult::valid),
                count(results, ClauseResult::causeCorrect),
                count(results, ClauseResult::checkCorrect),
                count(results, ClauseResult::clauseGroundingValid),
                count(results, ClauseResult::experimentalUnsupportedClaim),
                results.stream().mapToInt(ClauseResult::rawOutputCharacters).average().orElse(0),
                percentile(process, 0.50),
                percentile(process, 0.95),
                percentileDouble(prompt, 0.50),
                percentileDouble(prompt, 0.95),
                percentileDouble(generation, 0.50),
                percentileDouble(generation, 0.95));
    }

    private int count(
            List<ClauseResult> results, java.util.function.Predicate<ClauseResult> predicate) {
        return (int) results.stream().filter(predicate).count();
    }

    private long percentile(List<Long> values, double percentile) {
        return values.get(Math.max(0, (int) Math.ceil(values.size() * percentile) - 1));
    }

    private double percentileDouble(List<Double> values, double percentile) {
        return values.get(Math.max(0, (int) Math.ceil(values.size() * percentile) - 1));
    }

    private void verifyArtifacts(Configuration configuration) throws Exception {
        assertThat(configuration.executable()).isRegularFile();
        assertThat(configuration.model()).isRegularFile();
        assertThat(sha256(configuration.model())).isEqualTo(MODEL_SHA256);
        assertThat(sha256(configuration.executable())).isEqualTo(EXECUTABLE_SHA256);
    }

    private void reduceEnvironment(Map<String, String> environment) {
        String systemRoot = environment.get("SystemRoot");
        String path = environment.get("PATH");
        environment.clear();
        if (systemRoot != null) {
            environment.put("SystemRoot", systemRoot);
        }
        if (path != null) {
            environment.put("PATH", path);
        }
    }

    private double timing(Pattern pattern, String log) {
        Matcher matcher = pattern.matcher(log);
        double value = -1;
        while (matcher.find()) {
            value = Double.parseDouble(matcher.group(1));
        }
        return value;
    }

    private boolean containsThinking(String output) {
        String normalized = output.toLowerCase(Locale.ROOT);
        return normalized.contains("<think>")
                || normalized.contains("</think>")
                || normalized.contains("reasoning_content");
    }

    private String modelOutput(String runtimeOutput) {
        String value = runtimeOutput.strip();
        String marker = "[end of text]";
        return value.endsWith(marker)
                ? value.substring(0, value.length() - marker.length()).strip()
                : value;
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[1_048_576];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void destroyProcessTree(Process process) {
        process.descendants()
                .sorted(Comparator.comparingLong(ProcessHandle::pid).reversed())
                .forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private void deleteRunDirectory(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                    // Cleanup does not alter experimental scoring.
                                }
                            });
        } catch (IOException ignored) {
            // Cleanup does not alter experimental scoring.
        }
    }

    private record Configuration(
            Path executable,
            Path model,
            Duration timeout,
            int contextSize,
            int maximumOutputTokens,
            int threads,
            Path outputDirectory) {

        private static Configuration fromSystemProperties() throws IOException {
            Path output =
                    Path.of(
                                    System.getProperty(
                                            "stackmate.llamacpp.output",
                                            "target/phase-5-1-clause-grounding"))
                            .toAbsolutePath()
                            .normalize();
            Files.createDirectories(output);
            return new Configuration(
                    requiredPath("stackmate.llamacpp.executable"),
                    requiredPath("stackmate.llamacpp.model"),
                    Duration.ofSeconds(
                            Long.parseLong(
                                    System.getProperty(
                                            "stackmate.llamacpp.timeout-seconds", "120"))),
                    Integer.parseInt(System.getProperty("stackmate.llamacpp.context-size", "2048")),
                    Integer.parseInt(
                            System.getProperty("stackmate.llamacpp.maximum-output-tokens", "192")),
                    Integer.parseInt(System.getProperty("stackmate.llamacpp.threads", "16")),
                    output);
        }

        private static Path requiredPath(String name) {
            String value = System.getProperty(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("missing required system property: " + name);
            }
            return Path.of(value.strip()).toAbsolutePath().normalize();
        }
    }

    private record QualificationInput(
            String caseId,
            boolean abstentionRequired,
            int expectedCauseCount,
            int expectedCheckCount,
            io.github.kalayciburak.stackmate.incident.IncidentSnapshot incident) {}

    private record Clause(String text, List<String> evidenceIds) {

        private Clause {
            evidenceIds = List.copyOf(evidenceIds);
        }
    }

    private record ClauseOutput(
            String schemaVersion,
            Clause summary,
            Clause explanation,
            List<String> selectedCauseIds,
            List<String> selectedCheckIds,
            List<String> limitations) {

        private ClauseOutput {
            selectedCauseIds = List.copyOf(selectedCauseIds);
            selectedCheckIds = List.copyOf(selectedCheckIds);
            limitations = List.copyOf(limitations);
        }
    }

    private record LaunchResult(
            String output,
            String outputSha256,
            int exitCode,
            boolean timedOut,
            boolean hiddenThinking,
            long processMillis,
            double promptEvalMillis,
            double generationMillis) {}

    private record ClauseResult(
            String caseId,
            boolean valid,
            boolean causeCorrect,
            boolean checkCorrect,
            boolean clauseGroundingValid,
            boolean experimentalUnsupportedClaim,
            boolean fallbackSucceeded,
            String rawOutputSha256,
            int rawOutputCharacters,
            long processMillis,
            double promptEvalMillis,
            double generationMillis,
            String summary,
            List<String> summaryEvidenceIds,
            String explanation,
            List<String> explanationEvidenceIds,
            List<String> selectedCauseIds,
            List<String> selectedCheckIds,
            List<String> limitations,
            List<String> validationIssues) {}

    private record ClauseSummary(
            int cases,
            int valid,
            int causeCorrect,
            int checkCorrect,
            int clauseGroundingValid,
            int experimentalUnsupportedClaims,
            double averageRawOutputCharacters,
            long medianProcessMillis,
            long p95ProcessMillis,
            double medianPromptEvalMillis,
            double p95PromptEvalMillis,
            double medianGenerationMillis,
            double p95GenerationMillis) {}

    private record ClauseReport(
            String schema,
            String dataset,
            String basePromptId,
            String basePromptVersion,
            String basePromptSha256,
            String experimentPromptSha256,
            int experimentPromptCharacters,
            int experimentPromptEstimatedTokens,
            String modelSha256,
            String executableSha256,
            int contextSize,
            int maximumOutputTokens,
            int seed,
            String generationProfile,
            String startedAt,
            String completedAt,
            ClauseSummary summary,
            List<ClauseResult> results) {}
}
