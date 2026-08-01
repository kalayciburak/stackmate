package io.github.kalayciburak.stackmate.inference;

import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import io.github.kalayciburak.stackmate.privacy.SanitizerConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Default bounded validator for model-produced structured output. */
public final class DefaultInferenceOutputValidator implements InferenceOutputValidator {

    private static final List<String> DEFAULT_FORBIDDEN_CLAIMS =
            List.of(
                    "guaranteed root cause",
                    "definitive root cause",
                    "certain root cause",
                    "kesin kök neden",
                    "garanti kök neden",
                    "ignore previous instructions",
                    "system prompt");
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
    private static final Set<String> TURKISH_MARKERS =
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

    /**
     * Creates a validator with the default claim policy and an application-configured sanitizer.
     */
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
        Map<String, InferenceCauseCandidate> causes = indexCauses(request.causes());
        Map<String, InferenceCheckCandidate> checks = indexChecks(request.checks());
        Set<String> facts = ids(request.facts().stream().map(InferenceFact::id).toList());
        Set<String> locations =
                ids(request.sourceLocations().stream().map(SourceLocation::id).toList());

        if (output.language() != request.language()) {
            issue(
                    issues,
                    OutputValidationIssue.Code.LANGUAGE_MISMATCH,
                    "language",
                    "output language does not match the request");
        }
        validateText(output.summary(), "summary", request, issues);
        validateDuplicateSelections(
                output.causes().stream().map(StructuredCause::causeId).toList(), "causes", issues);
        validateDuplicateSelections(
                output.checks().stream().map(StructuredCheck::checkId).toList(), "checks", issues);

        for (int index = 0; index < output.causes().size(); index++) {
            StructuredCause cause = output.causes().get(index);
            String path = "causes[" + index + "]";
            InferenceCauseCandidate candidate = causes.get(cause.causeId());
            if (candidate == null) {
                issue(
                        issues,
                        OutputValidationIssue.Code.UNKNOWN_CAUSE_ID,
                        path + ".causeId",
                        "cause ID was not supplied");
            }
            validateReferences(cause.factIds(), facts, path + ".factIds", issues, true);
            validateReferences(
                    cause.sourceLocationIds(),
                    locations,
                    path + ".sourceLocationIds",
                    issues,
                    false);
            if (candidate != null && !candidate.factIds().containsAll(cause.factIds())) {
                issue(
                        issues,
                        OutputValidationIssue.Code.UNSUPPORTED_REFERENCE,
                        path + ".factIds",
                        "cause references a fact that does not support the supplied candidate");
            }
            validateText(cause.explanation(), path + ".explanation", request, issues);
        }
        for (int index = 0; index < output.checks().size(); index++) {
            StructuredCheck check = output.checks().get(index);
            String path = "checks[" + index + "]";
            InferenceCheckCandidate candidate = checks.get(check.checkId());
            if (candidate == null) {
                issue(
                        issues,
                        OutputValidationIssue.Code.UNKNOWN_CHECK_ID,
                        path + ".checkId",
                        "check ID was not supplied");
            }
            validateReferences(check.factIds(), facts, path + ".factIds", issues, true);
            validateReferences(
                    check.sourceLocationIds(),
                    locations,
                    path + ".sourceLocationIds",
                    issues,
                    false);
            if (candidate != null && !candidate.factIds().containsAll(check.factIds())) {
                issue(
                        issues,
                        OutputValidationIssue.Code.UNSUPPORTED_REFERENCE,
                        path + ".factIds",
                        "check references a fact that does not support the supplied candidate");
            }
            validateText(check.instruction(), path + ".instruction", request, issues);
        }
        if (outputCharacters(output) > request.maximumOutputCharacters()) {
            issue(
                    issues,
                    OutputValidationIssue.Code.EXCESSIVE_LENGTH,
                    "$",
                    "output exceeds the request limit");
        }
        if (issues.isEmpty()) {
            return new OutputValidationResult.Valid(new ValidatedInferenceOutput(output));
        }
        return new OutputValidationResult.Invalid(issues);
    }

    private void validateText(
            String text,
            String path,
            InferenceRequest request,
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
        if (containsFabricatedSourceReference(text, request.sourceLocations())) {
            issue(
                    issues,
                    OutputValidationIssue.Code.FABRICATED_SOURCE_REFERENCE,
                    path,
                    "text contains a source reference that was not supplied");
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
        if (hasOppositeLanguageOnly(text, request.language())) {
            issue(
                    issues,
                    OutputValidationIssue.Code.LANGUAGE_MISMATCH,
                    path,
                    "text appears to use a different language");
        }
    }

    private static boolean containsUnsafeControlCharacter(String value) {
        return value.codePoints()
                .anyMatch(
                        codePoint ->
                                Character.isISOControl(codePoint)
                                        && codePoint != '\n'
                                        && codePoint != '\t');
    }

    private static boolean containsFabricatedSourceReference(
            String text, List<SourceLocation> sourceLocations) {
        MatcherIterator fileMatches = new MatcherIterator(FILE_AND_LINE.matcher(text));
        while (fileMatches.hasNext()) {
            var match = fileMatches.next();
            String fileName = match.group(1);
            int lineNumber = Integer.parseInt(match.group(2));
            boolean known =
                    sourceLocations.stream()
                            .anyMatch(
                                    location ->
                                            location.fileName().equals(fileName)
                                                    && location.lineNumber() == lineNumber);
            if (!known) {
                return true;
            }
        }
        MatcherIterator methodMatches = new MatcherIterator(CLASS_AND_METHOD.matcher(text));
        while (methodMatches.hasNext()) {
            var match = methodMatches.next();
            boolean known =
                    sourceLocations.stream()
                            .anyMatch(
                                    location ->
                                            simpleName(location.className()).equals(match.group(1))
                                                    && location.methodName()
                                                            .equals(match.group(2)));
            if (!known) {
                return true;
            }
        }
        MatcherIterator classMatches = new MatcherIterator(QUALIFIED_CLASS.matcher(text));
        while (classMatches.hasNext()) {
            String className = classMatches.next().group();
            if (sourceLocations.stream()
                    .noneMatch(location -> location.className().equals(className))) {
                return true;
            }
        }
        return false;
    }

    private static String simpleName(String className) {
        int separator = className.lastIndexOf('.');
        return separator < 0 ? className : className.substring(separator + 1);
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

    private static final class MatcherIterator {

        private final java.util.regex.Matcher matcher;

        private MatcherIterator(java.util.regex.Matcher matcher) {
            this.matcher = matcher;
        }

        private boolean hasNext() {
            return matcher.find();
        }

        private java.util.regex.MatchResult next() {
            return matcher.toMatchResult();
        }
    }

    private static boolean hasOppositeLanguageOnly(String text, InferenceLanguage expected) {
        String[] words = text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+");
        Set<String> expectedMarkers =
                expected == InferenceLanguage.ENGLISH ? ENGLISH_MARKERS : TURKISH_MARKERS;
        Set<String> oppositeMarkers =
                expected == InferenceLanguage.ENGLISH ? TURKISH_MARKERS : ENGLISH_MARKERS;
        int expectedCount = 0;
        int oppositeCount = 0;
        for (String word : words) {
            if (expectedMarkers.contains(word)) {
                expectedCount++;
            }
            if (oppositeMarkers.contains(word)) {
                oppositeCount++;
            }
        }
        return expectedCount == 0 && oppositeCount >= 2;
    }

    private static void validateReferences(
            List<String> references,
            Set<String> known,
            String path,
            List<OutputValidationIssue> issues,
            boolean fact) {
        for (String reference : references) {
            if (!known.contains(reference)) {
                issue(
                        issues,
                        fact
                                ? OutputValidationIssue.Code.UNKNOWN_FACT_ID
                                : OutputValidationIssue.Code.UNKNOWN_SOURCE_LOCATION,
                        path,
                        fact ? "fact ID was not supplied" : "source location was not supplied");
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

    private static Map<String, InferenceCauseCandidate> indexCauses(
            List<InferenceCauseCandidate> values) {
        Map<String, InferenceCauseCandidate> result = new HashMap<>();
        values.forEach(value -> result.put(value.id(), value));
        return result;
    }

    private static Map<String, InferenceCheckCandidate> indexChecks(
            List<InferenceCheckCandidate> values) {
        Map<String, InferenceCheckCandidate> result = new HashMap<>();
        values.forEach(value -> result.put(value.id(), value));
        return result;
    }

    private static Set<String> ids(List<String> values) {
        return Set.copyOf(values);
    }

    private static int outputCharacters(StructuredInferenceOutput output) {
        long count = output.summary().length();
        count +=
                output.causes().stream()
                        .mapToLong(
                                cause ->
                                        cause.causeId().length()
                                                + cause.explanation().length()
                                                + joinedLength(cause.factIds())
                                                + joinedLength(cause.sourceLocationIds()))
                        .sum();
        count +=
                output.checks().stream()
                        .mapToLong(
                                check ->
                                        check.checkId().length()
                                                + check.instruction().length()
                                                + joinedLength(check.factIds())
                                                + joinedLength(check.sourceLocationIds()))
                        .sum();
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
