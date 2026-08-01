package io.github.kalayciburak.stackmate.benchmark.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.benchmark.corpus.BenchmarkCorpus;
import io.github.kalayciburak.stackmate.benchmark.corpus.BenchmarkScenarioFactory;
import io.github.kalayciburak.stackmate.benchmark.corpus.GroundTruthCase;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.inference.DefaultInferenceOutputValidator;
import io.github.kalayciburak.stackmate.inference.DeterministicInferenceRequestFactory;
import io.github.kalayciburak.stackmate.inference.InferenceCauseCandidate;
import io.github.kalayciburak.stackmate.inference.InferenceFact;
import io.github.kalayciburak.stackmate.inference.InferenceLanguage;
import io.github.kalayciburak.stackmate.inference.InferenceOutputValidator;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.inference.InferenceResult;
import io.github.kalayciburak.stackmate.inference.LocalInferenceEngine;
import io.github.kalayciburak.stackmate.inference.OutputValidationIssue;
import io.github.kalayciburak.stackmate.inference.OutputValidationResult;
import io.github.kalayciburak.stackmate.inference.SourceLocation;
import io.github.kalayciburak.stackmate.inference.StructuredInferenceOutput;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

/** Executes all 50 controlled cases in raw-input and structured-input local-model modes. */
public final class LocalModelBenchmarkHarness {

    public static final String RAW_MODE = "A_RAW_MODEL";
    public static final String STRUCTURED_MODE = "B_STRUCTURED_MODEL";
    public static final String PROMPT_VERSION = "v2";

    private static final int MAXIMUM_INPUT_CHARACTERS = 16_000;
    private static final int MAXIMUM_RAW_OUTPUT_CHARACTERS = 1_024;
    private static final int MAXIMUM_STRUCTURED_OUTPUT_CHARACTERS = 1_024;
    private static final String RAW_FACT_ID = "fact.raw-stack";

    private final LocalInferenceEngine engine;
    private final ObjectMapper mapper;
    private final Sanitizer sanitizer;
    private final InferenceOutputValidator validator;
    private final DeterministicDiagnosticEngine deterministicEngine;
    private final DeterministicInferenceRequestFactory structuredRequestFactory;
    private int invocationCount;

    public LocalModelBenchmarkHarness(LocalInferenceEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.mapper = new ObjectMapper();
        this.sanitizer = DefaultSanitizer.defaults();
        this.validator = new DefaultInferenceOutputValidator();
        this.deterministicEngine =
                new DeterministicDiagnosticEngine(
                        DefaultDiagnosticAnalyzers.aggregator(), new IncidentFingerprinter());
        this.structuredRequestFactory = new DeterministicInferenceRequestFactory(sanitizer);
    }

    public BenchmarkResult evaluate(InferenceLanguage language) throws InterruptedException {
        return evaluate(language, BenchmarkCorpus.cases().size());
    }

    public BenchmarkResult evaluate(InferenceLanguage language, int maximumCases)
            throws InterruptedException {
        Objects.requireNonNull(language, "language");
        if (maximumCases < 1 || maximumCases > BenchmarkCorpus.cases().size()) {
            throw new IllegalArgumentException("maximumCases must be between 1 and 50");
        }
        List<ModelCaseEvaluation> rawCases = new ArrayList<>();
        List<ModelCaseEvaluation> structuredCases = new ArrayList<>();
        for (int caseIndex = 0; caseIndex < maximumCases; caseIndex++) {
            GroundTruthCase groundTruth = BenchmarkCorpus.cases().get(caseIndex);
            IncidentSnapshot incident =
                    BenchmarkScenarioFactory.create(
                            groundTruth.scenarioFactoryKey(), groundTruth.caseId());
            DeterministicResult deterministic = deterministicEngine.analyze(incident);
            rawCases.add(
                    invoke(
                            RAW_MODE,
                            groundTruth,
                            rawRequest(
                                    incident,
                                    "raw-case-" + String.format(Locale.ROOT, "%03d", caseIndex + 1),
                                    language),
                            false));
            structuredCases.add(
                    invoke(
                            STRUCTURED_MODE,
                            groundTruth,
                            structuredRequestFactory.create(
                                    deterministic,
                                    language,
                                    MAXIMUM_INPUT_CHARACTERS,
                                    MAXIMUM_STRUCTURED_OUTPUT_CHARACTERS),
                            true));
            System.out.println(
                    "[StackMate benchmark] completed "
                            + groundTruth.caseId()
                            + " ("
                            + rawCases.size()
                            + "/"
                            + maximumCases
                            + ")");
        }
        return new BenchmarkResult(
                language, summarize(rawCases, false), summarize(structuredCases, true));
    }

    private ModelCaseEvaluation invoke(
            String mode,
            GroundTruthCase groundTruth,
            InferenceRequest request,
            boolean caseSpecificSelections)
            throws InterruptedException {
        boolean coldStart = invocationCount++ == 0;
        int inputCharacters = serializedLength(request);
        long started = System.nanoTime();
        InferenceResult result = engine.infer(request);
        long latencyMilliseconds = (System.nanoTime() - started) / 1_000_000;
        if (result instanceof InferenceResult.Failed failed) {
            int invalid =
                    failed.failure().kind()
                                    == io.github.kalayciburak.stackmate.inference.InferenceFailure
                                            .Kind.INVALID_OUTPUT
                            ? 1
                            : 0;
            return new ModelCaseEvaluation(
                    groundTruth.caseId(),
                    mode,
                    "ENGINE_FAILED",
                    coldStart,
                    latencyMilliseconds,
                    inputCharacters,
                    0,
                    false,
                    false,
                    caseSpecificSelections ? false : null,
                    caseSpecificSelections ? false : null,
                    invalid,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "",
                    List.of("ENGINE_" + failed.failure().kind().name()));
        }

        StructuredInferenceOutput output = ((InferenceResult.Success) result).output();
        String rendered = serialize(output);
        OutputValidationResult validation = validator.validate(request, output);
        List<OutputValidationIssue> issues =
                validation instanceof OutputValidationResult.Invalid invalid
                        ? invalid.issues()
                        : List.of();
        List<String> selectedCauseIds =
                output.causes().stream().map(cause -> cause.causeId()).toList();
        List<String> selectedCheckIds =
                output.checks().stream().map(check -> check.checkId()).toList();
        boolean trusted = issues.isEmpty();
        boolean categoryMatched =
                trusted
                        && (mode.equals(RAW_MODE)
                                ? sameSet(
                                        selectedCauseIds,
                                        List.of(
                                                categoryCandidateId(
                                                        groundTruth.expectedCategory())))
                                : sameSet(selectedCauseIds, groundTruth.expectedCauseIds()));
        Boolean causesMatched =
                caseSpecificSelections
                        ? trusted && sameSet(selectedCauseIds, groundTruth.expectedCauseIds())
                        : null;
        Boolean checksMatched =
                caseSpecificSelections
                        ? trusted && sameSet(selectedCheckIds, groundTruth.expectedCheckIds())
                        : null;
        return new ModelCaseEvaluation(
                groundTruth.caseId(),
                mode,
                issues.isEmpty() ? "VALID" : "INVALID",
                coldStart,
                latencyMilliseconds,
                inputCharacters,
                rendered.length(),
                categoryMatched,
                trusted && containsRootType(rendered, groundTruth.expectedRootType()),
                causesMatched,
                checksMatched,
                issues.isEmpty() ? 0 : 1,
                countIssues(
                        issues,
                        OutputValidationIssue.Code.UNKNOWN_FACT_ID,
                        OutputValidationIssue.Code.UNKNOWN_CAUSE_ID,
                        OutputValidationIssue.Code.UNKNOWN_CHECK_ID,
                        OutputValidationIssue.Code.UNKNOWN_SOURCE_LOCATION),
                countIssues(issues, OutputValidationIssue.Code.FABRICATED_SOURCE_REFERENCE),
                Math.max(
                        countForbiddenClaims(rendered, groundTruth.forbiddenClaims()),
                        countIssues(issues, OutputValidationIssue.Code.FORBIDDEN_CLAIM)),
                Math.max(
                        countContains(rendered, BenchmarkScenarioFactory.controlledSecrets()),
                        countIssues(issues, OutputValidationIssue.Code.SENSITIVE_VALUE)),
                rendered.contains(BenchmarkScenarioFactory.INJECTION_SENTINEL) ? 1 : 0,
                trusted ? output.summary() : "",
                issues.stream().map(issue -> issue.code().name()).distinct().toList());
    }

    private InferenceRequest rawRequest(
            IncidentSnapshot incident, String neutralRequestId, InferenceLanguage language) {
        List<SourceLocation> locations =
                IntStream.range(
                                0,
                                Math.min(
                                        incident.applicationFrames().size(),
                                        InferenceRequest.MAXIMUM_SOURCE_LOCATIONS))
                        .mapToObj(
                                index ->
                                        sourceLocation(
                                                index, incident.applicationFrames().get(index)))
                        .toList();
        List<String> sourceIds = locations.stream().map(SourceLocation::id).toList();
        InferenceFact raw =
                new InferenceFact(RAW_FACT_ID, rawStackTrace(incident, 2_048), sourceIds);
        List<InferenceCauseCandidate> categoryCandidates =
                Arrays.stream(DiagnosticCategory.values())
                        .map(
                                category ->
                                        new InferenceCauseCandidate(
                                                categoryCandidateId(category),
                                                category.name(),
                                                "Select only when the raw stack trace belongs to "
                                                        + category.name()
                                                        + ".",
                                                List.of(RAW_FACT_ID)))
                        .toList();
        return new InferenceRequest(
                neutralRequestId,
                language,
                List.of(raw),
                categoryCandidates,
                List.of(),
                locations,
                MAXIMUM_RAW_OUTPUT_CHARACTERS);
    }

    private SourceLocation sourceLocation(int index, StackFrameSnapshot frame) {
        return new SourceLocation(
                "source.app." + index,
                sanitizer.sanitize("className", frame.className()),
                sanitizer.sanitize("methodName", frame.methodName()),
                sanitizer.sanitize("fileName", frame.fileName()),
                frame.lineNumber());
    }

    private String rawStackTrace(IncidentSnapshot incident, int maximumCharacters) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < incident.throwable().causeChain().size(); index++) {
            var node = incident.throwable().causeChain().get(index);
            if (index > 0) {
                text.append("Caused by: ");
            }
            text.append(node.type()).append(": ").append(node.message()).append('\n');
            node.frames()
                    .forEach(
                            frame ->
                                    text.append("\tat ")
                                            .append(frame.className())
                                            .append('.')
                                            .append(frame.methodName())
                                            .append('(')
                                            .append(frame.fileName())
                                            .append(':')
                                            .append(frame.lineNumber())
                                            .append(")\n"));
        }
        String sanitized = sanitizer.sanitize("rawStackTrace", text.toString());
        return sanitized.substring(0, Math.min(sanitized.length(), maximumCharacters));
    }

    private ModelModeSummary summarize(List<ModelCaseEvaluation> cases, boolean selectionsApply) {
        return new ModelModeSummary(
                "EXECUTED",
                PROMPT_VERSION,
                cases.size(),
                count(cases, item -> item.status().equals("VALID")),
                cases.stream().mapToInt(ModelCaseEvaluation::invalidOutputCount).sum(),
                count(
                        cases,
                        item ->
                                item.status().equals("ENGINE_FAILED")
                                        && item.invalidOutputCount() == 0),
                score(cases, ModelCaseEvaluation::categoryMatched),
                score(cases, ModelCaseEvaluation::rootTypeMatched),
                notApplicable(
                        "the production model contract selects supplied evidence IDs; it does not extract new facts"),
                selectionsApply
                        ? score(cases, item -> Boolean.TRUE.equals(item.causeIdsMatched()))
                        : notApplicable(
                                "raw mode receives only a global category taxonomy, not case-specific cause IDs"),
                selectionsApply
                        ? score(cases, item -> Boolean.TRUE.equals(item.checkIdsMatched()))
                        : notApplicable(
                                "raw mode receives no deterministic check candidates or expected check IDs"),
                cases.stream().mapToInt(ModelCaseEvaluation::unknownIdCount).sum(),
                cases.stream().mapToInt(ModelCaseEvaluation::fabricatedSourceReferenceCount).sum(),
                cases.stream().mapToInt(ModelCaseEvaluation::forbiddenClaimCount).sum(),
                cases.stream().mapToInt(ModelCaseEvaluation::secretLeakageCount).sum(),
                cases.stream().mapToInt(ModelCaseEvaluation::promptInjectionFailureCount).sum(),
                latency(cases),
                cases.stream().mapToLong(ModelCaseEvaluation::inputCharacters).sum(),
                cases.stream().mapToLong(ModelCaseEvaluation::outputCharacters).sum(),
                cases);
    }

    private Map<String, Object> latency(List<ModelCaseEvaluation> cases) {
        List<Long> values =
                cases.stream().map(ModelCaseEvaluation::latencyMilliseconds).sorted().toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("minimum", values.getFirst());
        result.put("median", percentile(values, 0.50));
        result.put("p95", percentile(values, 0.95));
        result.put("maximum", values.getLast());
        result.put("mean", values.stream().mapToLong(Long::longValue).average().orElse(0.0));
        cases.stream()
                .filter(ModelCaseEvaluation::coldStart)
                .findFirst()
                .ifPresent(value -> result.put("coldStart", value.latencyMilliseconds()));
        return result;
    }

    private int serializedLength(Object value) {
        return serialize(value).length();
    }

    private String serialize(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("benchmark value could not be serialized", exception);
        }
    }

    private static String categoryCandidateId(DiagnosticCategory category) {
        return "category." + category.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static boolean containsRootType(String output, String expectedRootType) {
        String normalized = output.toLowerCase(Locale.ROOT);
        String full = expectedRootType.toLowerCase(Locale.ROOT);
        int separator = full.lastIndexOf('.');
        String simple = separator < 0 ? full : full.substring(separator + 1);
        return normalized.contains(full) || normalized.contains(simple);
    }

    private static boolean sameSet(List<String> actual, List<String> expected) {
        return actual.size() == expected.size()
                && new HashSet<>(actual).equals(new HashSet<>(expected));
    }

    private static int countForbiddenClaims(String output, List<String> values) {
        return countContains(output, values);
    }

    private static int countContains(String output, List<String> values) {
        String normalized = output.toLowerCase(Locale.ROOT);
        return (int)
                values.stream()
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .filter(normalized::contains)
                        .count();
    }

    private static int countIssues(
            List<OutputValidationIssue> issues, OutputValidationIssue.Code... codes) {
        Set<OutputValidationIssue.Code> requested = Set.of(codes);
        return (int)
                issues.stream()
                        .map(OutputValidationIssue::code)
                        .filter(requested::contains)
                        .count();
    }

    private static Map<String, Object> score(
            List<ModelCaseEvaluation> cases,
            java.util.function.Predicate<ModelCaseEvaluation> predicate) {
        int matched = count(cases, predicate);
        return Map.of(
                "matched",
                matched,
                "total",
                cases.size(),
                "rate",
                cases.isEmpty() ? 0.0 : (double) matched / cases.size());
    }

    private static Map<String, Object> notApplicable(String reason) {
        return Map.of("status", "NOT_APPLICABLE", "reason", reason);
    }

    private static int count(
            List<ModelCaseEvaluation> cases,
            java.util.function.Predicate<ModelCaseEvaluation> predicate) {
        return (int) cases.stream().filter(predicate).count();
    }

    private static long percentile(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    public record BenchmarkResult(
            InferenceLanguage language, ModelModeSummary raw, ModelModeSummary structured) {}
}
