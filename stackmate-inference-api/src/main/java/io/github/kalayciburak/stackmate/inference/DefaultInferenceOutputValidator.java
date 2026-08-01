package io.github.kalayciburak.stackmate.inference;

import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import io.github.kalayciburak.stackmate.privacy.SanitizerConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Default bounded validator for evidence-only model explanations. */
public final class DefaultInferenceOutputValidator implements InferenceOutputValidator {

    private static final List<String> DEFAULT_FORBIDDEN_CLAIMS =
            List.of(
                    "guaranteed root cause",
                    "definitive root cause",
                    "certain root cause",
                    "kesin kök neden",
                    "garanti kök neden",
                    "ignore previous instructions",
                    "system prompt",
                    "source code was inspected",
                    "repository was inspected",
                    "kaynak kodu inceledim",
                    "depoyu inceledim",
                    "network access",
                    "internet access");
    private static final Pattern NUMERIC_CONFIDENCE =
            Pattern.compile("(?iu)(confidence|güven(?:\\s+puanı)?)\\s*[:=]?\\s*\\d");
    private static final Pattern FILE_AND_LINE =
            Pattern.compile("(?<![\\p{L}\\p{N}_$.-])([\\p{L}\\p{N}_$.-]+\\.java):(\\d+)(?!\\d)");
    private static final Pattern CLASS_AND_METHOD =
            Pattern.compile(
                    "(?<![\\p{L}\\p{N}_$])([\\p{L}_$][\\p{L}\\p{N}_$]*)#([\\p{L}_$][\\p{L}\\p{N}_$]*)");
    private static final Pattern QUALIFIED_CLASS =
            Pattern.compile(
                    "(?<![\\p{L}\\p{N}_$])(?:[a-z_][\\p{L}\\p{N}_$]*\\.)+[A-Z][\\p{L}\\p{N}_$]*(?![\\p{L}\\p{N}_$])");
    private static final Pattern SOURCE_FILE =
            Pattern.compile(
                    "(?<![\\p{L}\\p{N}_$.-])[\\p{L}\\p{N}_$.-]+\\.java(?![\\p{L}\\p{N}_$.-])");
    private static final Pattern SNAKE_IDENTIFIER =
            Pattern.compile(
                    "(?<![\\p{L}\\p{N}_])(?:[a-z][a-z0-9]*_){1,}[a-z0-9]+(?![\\p{L}\\p{N}_])");
    private static final Pattern TECHNICAL_CAMEL_IDENTIFIER =
            Pattern.compile(
                    "(?<![\\p{L}\\p{N}_$])[A-Z][\\p{L}\\p{N}_$]*(?:Controller|Service|Repository|Exception)(?![\\p{L}\\p{N}_$])");
    private static final Pattern DESTRUCTIVE_COMMAND =
            Pattern.compile(
                    "(?iu)(?:rm\\s+-rf|remove-item(?:\\s+-recurse)?|drop\\s+(?:table|database)|format\\s+[a-z]:|del\\s+/[sfq]|shutdown\\s+(?:/s|-h))");
    private static final Set<String> ENGLISH_MARKERS =
            Set.of(
                    "a",
                    "an",
                    "the",
                    "is",
                    "was",
                    "were",
                    "in",
                    "with",
                    "this",
                    "that",
                    "because",
                    "cause",
                    "check",
                    "failure",
                    "failed",
                    "error",
                    "operation",
                    "invalid",
                    "state",
                    "request",
                    "value");
    private static final Set<String> NON_ENGLISH_MARKERS =
            Set.of(
                    "bir",
                    "bu",
                    "şu",
                    "çünkü",
                    "neden",
                    "kontrol",
                    "hata",
                    "olabilir",
                    "için",
                    "işlem",
                    "geçersiz",
                    "durum",
                    "başarısız",
                    "istek",
                    "değer",
                    "değil");

    private final List<String> forbiddenClaims;
    private final Sanitizer sanitizer;

    /** Creates a validator with the default forbidden-claim policy. */
    public DefaultInferenceOutputValidator() {
        this(DEFAULT_FORBIDDEN_CLAIMS, defaultOutputSanitizer());
    }

    /** Creates a validator with the default claim policy and a configured sanitizer. */
    public DefaultInferenceOutputValidator(Sanitizer sanitizer) {
        this(DEFAULT_FORBIDDEN_CLAIMS, sanitizer);
    }

    /** Creates a validator with an application-provided forbidden-claim policy. */
    public DefaultInferenceOutputValidator(List<String> forbiddenClaims) {
        this(forbiddenClaims, defaultOutputSanitizer());
    }

    /** Creates a validator using an application-configured sanitizer. */
    public DefaultInferenceOutputValidator(List<String> forbiddenClaims, Sanitizer sanitizer) {
        Objects.requireNonNull(forbiddenClaims, "forbiddenClaims");
        this.forbiddenClaims =
                forbiddenClaims.stream()
                        .map(value -> Contracts.requireBoundedText(value, "forbiddenClaim", 256))
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .distinct()
                        .toList();
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    @Override
    public OutputValidationResult validate(
            InferenceRequest request, StructuredInferenceOutput output) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(output, "output");
        List<OutputValidationIssue> issues = new ArrayList<>();
        Set<String> causes =
                ids(request.candidateCauses().stream().map(InferenceCauseCandidate::id).toList());
        Set<String> checks =
                ids(request.candidateChecks().stream().map(InferenceCheckCandidate::id).toList());
        Set<String> facts = ids(request.facts().stream().map(InferenceFact::id).toList());
        String allowedText = allowedText(request);

        if (!output.schemaVersion().equals(request.schemaVersion())) {
            issue(
                    issues,
                    OutputValidationIssue.Code.UNKNOWN_SCHEMA_VERSION,
                    "schemaVersion",
                    "output schema version does not match the request");
        }
        validateReferences(
                output.selectedCauseIds(),
                causes,
                "selectedCauseIds",
                OutputValidationIssue.Code.UNKNOWN_CAUSE_ID,
                "cause ID was not supplied",
                issues);
        validateReferences(
                output.selectedCheckIds(),
                checks,
                "selectedCheckIds",
                OutputValidationIssue.Code.UNKNOWN_CHECK_ID,
                "check ID was not supplied",
                issues);
        validateReferences(
                output.usedEvidenceIds(),
                facts,
                "usedEvidenceIds",
                OutputValidationIssue.Code.UNKNOWN_FACT_ID,
                "evidence ID was not supplied",
                issues);
        validateDuplicateSelections(output.selectedCauseIds(), "selectedCauseIds", issues);
        validateDuplicateSelections(output.selectedCheckIds(), "selectedCheckIds", issues);
        validateDuplicateSelections(output.usedEvidenceIds(), "usedEvidenceIds", issues);
        validateCandidateSupport(request, output, issues);

        validateText(output.summary(), "summary", request, allowedText, issues);
        validateText(output.explanation(), "explanation", request, allowedText, issues);
        for (int index = 0; index < output.limitations().size(); index++) {
            validateText(
                    output.limitations().get(index),
                    "limitations[" + index + "]",
                    request,
                    allowedText,
                    issues);
        }
        if (outputCharacters(output) > request.maximumOutputCharacters()) {
            issue(
                    issues,
                    OutputValidationIssue.Code.EXCESSIVE_LENGTH,
                    "$",
                    "output exceeds the request limit");
        }
        if (issues.isEmpty()) {
            return new OutputValidationResult.Valid(new ValidatedInferenceOutput(request, output));
        }
        return new OutputValidationResult.Invalid(issues);
    }

    private void validateCandidateSupport(
            InferenceRequest request,
            StructuredInferenceOutput output,
            List<OutputValidationIssue> issues) {
        Set<String> used = Set.copyOf(output.usedEvidenceIds());
        request.candidateCauses().stream()
                .filter(candidate -> output.selectedCauseIds().contains(candidate.id()))
                .filter(candidate -> candidate.evidenceIds().stream().noneMatch(used::contains))
                .forEach(
                        candidate ->
                                issue(
                                        issues,
                                        OutputValidationIssue.Code.UNSUPPORTED_REFERENCE,
                                        "selectedCauseIds",
                                        "selected cause has no used supporting evidence"));
        request.candidateChecks().stream()
                .filter(candidate -> output.selectedCheckIds().contains(candidate.id()))
                .filter(candidate -> candidate.evidenceIds().stream().noneMatch(used::contains))
                .forEach(
                        candidate ->
                                issue(
                                        issues,
                                        OutputValidationIssue.Code.UNSUPPORTED_REFERENCE,
                                        "selectedCheckIds",
                                        "selected check has no used supporting evidence"));
    }

    private void validateText(
            String text,
            String path,
            InferenceRequest request,
            String allowedText,
            List<OutputValidationIssue> issues) {
        if (containsUnsafeControlCharacter(text)) {
            issue(
                    issues,
                    OutputValidationIssue.Code.CONTROL_CHARACTER,
                    path,
                    "text contains an unsafe control character");
        }
        if (!sanitizer.sanitize(text).equals(text)) {
            issue(
                    issues,
                    OutputValidationIssue.Code.SENSITIVE_VALUE,
                    path,
                    "text contains sensitive or unsafe data");
        }
        if (text.contains("```")) {
            issue(
                    issues,
                    OutputValidationIssue.Code.MARKDOWN_FENCE,
                    path,
                    "text contains a Markdown fence");
        }
        if (DESTRUCTIVE_COMMAND.matcher(text).find()) {
            issue(
                    issues,
                    OutputValidationIssue.Code.DESTRUCTIVE_COMMAND,
                    path,
                    "text contains a destructive command");
        }
        if (containsFabricatedSourceReference(text, allowedText)) {
            issue(
                    issues,
                    OutputValidationIssue.Code.FABRICATED_SOURCE_REFERENCE,
                    path,
                    "text contains a source reference that was not supplied as evidence");
        }
        if (containsFabricatedIdentifier(text, allowedText)) {
            issue(
                    issues,
                    OutputValidationIssue.Code.FABRICATED_IDENTIFIER,
                    path,
                    "text contains a technical identifier that was not supplied");
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (forbiddenClaims.stream().anyMatch(normalized::contains)
                || NUMERIC_CONFIDENCE.matcher(text).find()) {
            issue(
                    issues,
                    OutputValidationIssue.Code.FORBIDDEN_CLAIM,
                    path,
                    "text contains a forbidden claim");
        }
        if (appearsNonEnglish(text)) {
            issue(
                    issues,
                    OutputValidationIssue.Code.OUTPUT_NOT_ENGLISH,
                    path,
                    "text does not appear to be English");
        }
    }

    private static String allowedText(InferenceRequest request) {
        StringBuilder text = new StringBuilder();
        request.facts()
                .forEach(
                        fact ->
                                text.append(fact.type())
                                        .append(' ')
                                        .append(fact.value())
                                        .append(' '));
        request.candidateCauses().forEach(candidate -> text.append(candidate.text()).append(' '));
        request.candidateChecks().forEach(candidate -> text.append(candidate.text()).append(' '));
        request.terminology()
                .forEach(
                        term ->
                                text.append(term.term())
                                        .append(' ')
                                        .append(term.definition())
                                        .append(' '));
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean containsUnsafeControlCharacter(String value) {
        return value.codePoints()
                .anyMatch(
                        codePoint ->
                                Character.isISOControl(codePoint)
                                        && codePoint != '\n'
                                        && codePoint != '\t');
    }

    private static boolean containsFabricatedSourceReference(String text, String allowedText) {
        return unknownMatch(FILE_AND_LINE, text, allowedText)
                || unknownMatch(CLASS_AND_METHOD, text, allowedText)
                || unknownMatch(QUALIFIED_CLASS, text, allowedText)
                || unknownMatch(SOURCE_FILE, text, allowedText);
    }

    private static boolean containsFabricatedIdentifier(String text, String allowedText) {
        return unknownMatch(SNAKE_IDENTIFIER, text, allowedText)
                || unknownMatch(TECHNICAL_CAMEL_IDENTIFIER, text, allowedText);
    }

    private static boolean unknownMatch(Pattern pattern, String text, String allowedText) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (!allowedText.contains(matcher.group().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static Sanitizer defaultOutputSanitizer() {
        SanitizerConfig defaults = SanitizerConfig.defaults();
        return new DefaultSanitizer(
                new SanitizerConfig(
                        StructuredInferenceOutput.MAXIMUM_TEXT_LENGTH,
                        defaults.replacement(),
                        defaults.redactEmail(),
                        defaults.redactTurkishIdentityNumber(),
                        defaults.redactPaymentCard(),
                        defaults.sensitiveFieldNames(),
                        defaults.customPatterns()));
    }

    private static boolean appearsNonEnglish(String text) {
        String[] words = text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+");
        int englishCount = 0;
        int nonEnglishCount = 0;
        for (String word : words) {
            if (ENGLISH_MARKERS.contains(word)) {
                englishCount++;
            }
            if (NON_ENGLISH_MARKERS.contains(word)) {
                nonEnglishCount++;
            }
        }
        return englishCount == 0 && nonEnglishCount >= 2;
    }

    private static void validateReferences(
            List<String> references,
            Set<String> known,
            String path,
            OutputValidationIssue.Code code,
            String message,
            List<OutputValidationIssue> issues) {
        for (String reference : references) {
            if (!known.contains(reference)) {
                issue(issues, code, path, message);
            }
        }
    }

    private static void validateDuplicateSelections(
            List<String> values, String path, List<OutputValidationIssue> issues) {
        if (new HashSet<>(values).size() != values.size()) {
            issue(
                    issues,
                    OutputValidationIssue.Code.DUPLICATE_SELECTION,
                    path,
                    "selection IDs must be unique");
        }
    }

    private static Set<String> ids(List<String> values) {
        return Set.copyOf(values);
    }

    private static int outputCharacters(StructuredInferenceOutput output) {
        long count =
                output.schemaVersion().length()
                        + output.summary().length()
                        + output.explanation().length()
                        + joinedLength(output.selectedCauseIds())
                        + joinedLength(output.selectedCheckIds())
                        + joinedLength(output.usedEvidenceIds())
                        + joinedLength(output.limitations());
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private static int joinedLength(List<String> values) {
        return values.stream().mapToInt(String::length).sum();
    }

    private static void issue(
            List<OutputValidationIssue> issues,
            OutputValidationIssue.Code code,
            String path,
            String message) {
        issues.add(new OutputValidationIssue(code, path, message));
    }
}
