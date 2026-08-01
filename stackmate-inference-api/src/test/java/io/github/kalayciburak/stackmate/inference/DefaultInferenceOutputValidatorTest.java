package io.github.kalayciburak.stackmate.inference;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.privacy.SanitizerConfig;
import io.github.kalayciburak.stackmate.privacy.SanitizerPattern;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DefaultInferenceOutputValidatorTest {

    private final DefaultInferenceOutputValidator validator = new DefaultInferenceOutputValidator();

    @Test
    void returnsTrustedOutputOnlyForKnownEvidenceBackedSelections() {
        StructuredInferenceOutput output = validOutput();

        OutputValidationResult result = validator.validate(request(2_000), output);

        assertThat(result)
                .isInstanceOfSatisfying(
                        OutputValidationResult.Valid.class,
                        valid -> {
                            assertThat(valid.output()).isNotSameAs(output);
                            assertThat(valid.output().summary())
                                    .isEqualTo("This failure may come from the database.");
                            assertThat(valid.output().causes()).hasSize(1);
                        });
    }

    @Test
    void rejectsUnknownFactCauseCheckAndSourceLocationIds() {
        StructuredInferenceOutput output =
                new StructuredInferenceOutput(
                        InferenceLanguage.ENGLISH,
                        "This is an error summary.",
                        List.of(
                                new StructuredCause(
                                        "cause.unknown",
                                        "This cause follows the error.",
                                        List.of("fact.unknown"),
                                        List.of("source.unknown"))),
                        List.of(
                                new StructuredCheck(
                                        "check.unknown",
                                        "Check this error.",
                                        List.of("fact.unknown"),
                                        List.of("source.unknown"))));

        assertCodes(
                validator.validate(request(2_000), output),
                OutputValidationIssue.Code.UNKNOWN_CAUSE_ID,
                OutputValidationIssue.Code.UNKNOWN_CHECK_ID,
                OutputValidationIssue.Code.UNKNOWN_FACT_ID,
                OutputValidationIssue.Code.UNKNOWN_SOURCE_LOCATION);
    }

    @Test
    void rejectsFactsThatDoNotSupportTheSelectedCandidate() {
        InferenceRequest request =
                new InferenceRequest(
                        "request.one",
                        InferenceLanguage.ENGLISH,
                        List.of(
                                new InferenceFact("fact.one", "First", List.of("source.one")),
                                new InferenceFact("fact.two", "Second", List.of("source.one"))),
                        List.of(
                                new InferenceCauseCandidate(
                                        "cause.one", "Cause", "Explanation", List.of("fact.one"))),
                        List.of(),
                        List.of(location()),
                        2_000);
        StructuredInferenceOutput output =
                new StructuredInferenceOutput(
                        InferenceLanguage.ENGLISH,
                        "This is the failure summary.",
                        List.of(
                                new StructuredCause(
                                        "cause.one",
                                        "This cause follows the error.",
                                        List.of("fact.two"),
                                        List.of("source.one"))),
                        List.of());

        assertCodes(
                validator.validate(request, output),
                OutputValidationIssue.Code.UNSUPPORTED_REFERENCE);
    }

    @Test
    void rejectsExcessiveOutputAndUnsafeControlCharacters() {
        StructuredInferenceOutput output =
                new StructuredInferenceOutput(
                        InferenceLanguage.ENGLISH,
                        "This error has an escape \u001Bsequence.",
                        List.of(),
                        List.of());

        assertCodes(
                validator.validate(request(10), output),
                OutputValidationIssue.Code.EXCESSIVE_LENGTH,
                OutputValidationIssue.Code.CONTROL_CHARACTER);
    }

    @Test
    void rejectsForbiddenClaimsAndNumericConfidence() {
        StructuredInferenceOutput output =
                new StructuredInferenceOutput(
                        InferenceLanguage.ENGLISH,
                        "This is the guaranteed root cause with confidence: 95.",
                        List.of(),
                        List.of());

        assertCodes(
                validator.validate(request(2_000), output),
                OutputValidationIssue.Code.FORBIDDEN_CLAIM);
    }

    @Test
    void rejectsSensitiveValuesAndFabricatedSourceReferences() {
        StructuredInferenceOutput output =
                new StructuredInferenceOutput(
                        InferenceLanguage.ENGLISH,
                        "Check com.attacker.Invented at Invented#run(Invented.java:999) with "
                                + "password=hunter2.",
                        List.of(),
                        List.of());

        assertCodes(
                validator.validate(request(2_000), output),
                OutputValidationIssue.Code.SENSITIVE_VALUE,
                OutputValidationIssue.Code.FABRICATED_SOURCE_REFERENCE);
    }

    @Test
    void rejectsValuesMatchedByTheApplicationSanitizerPolicy() {
        SanitizerConfig defaults = SanitizerConfig.defaults();
        var sanitizer =
                new DefaultSanitizer(
                        new SanitizerConfig(
                                2_000,
                                defaults.replacement(),
                                false,
                                false,
                                false,
                                Set.of(),
                                List.of(
                                        new SanitizerPattern(
                                                Pattern.compile("CUSTOM-[0-9]+"),
                                                defaults.replacement()))));
        var configuredValidator = new DefaultInferenceOutputValidator(sanitizer);
        var output =
                new StructuredInferenceOutput(
                        InferenceLanguage.ENGLISH,
                        "This error contains CUSTOM-4821.",
                        List.of(),
                        List.of());

        assertCodes(
                configuredValidator.validate(request(2_000), output),
                OutputValidationIssue.Code.SENSITIVE_VALUE);
    }

    @Test
    void acceptsOnlySuppliedSourceReferencesInModelText() {
        StructuredInferenceOutput output =
                new StructuredInferenceOutput(
                        InferenceLanguage.ENGLISH,
                        "This failure is visible in example.Repository at "
                                + "Repository#save(Repository.java:42).",
                        List.of(),
                        List.of());

        assertThat(validator.validate(request(2_000), output))
                .isInstanceOf(OutputValidationResult.Valid.class);
    }

    @Test
    void rejectsDeclaredAndDetectedLanguageMismatch() {
        StructuredInferenceOutput declaredMismatch =
                new StructuredInferenceOutput(
                        InferenceLanguage.TURKISH,
                        "Hata bu nedenle olabilir.",
                        List.of(),
                        List.of());
        StructuredInferenceOutput detectedMismatch =
                new StructuredInferenceOutput(
                        InferenceLanguage.ENGLISH,
                        "Bu hata çünkü neden olabilir.",
                        List.of(),
                        List.of());
        InferenceRequest englishRequest = request(2_000);
        InferenceRequest turkishRequest =
                new InferenceRequest(
                        englishRequest.id(),
                        InferenceLanguage.TURKISH,
                        englishRequest.facts(),
                        englishRequest.causes(),
                        englishRequest.checks(),
                        englishRequest.sourceLocations(),
                        englishRequest.maximumOutputCharacters());
        StructuredInferenceOutput copiedEnglishText =
                new StructuredInferenceOutput(
                        InferenceLanguage.TURKISH,
                        "An operation ran in an invalid state",
                        List.of(),
                        List.of());

        assertCodes(
                validator.validate(request(2_000), declaredMismatch),
                OutputValidationIssue.Code.LANGUAGE_MISMATCH);
        assertCodes(
                validator.validate(request(2_000), detectedMismatch),
                OutputValidationIssue.Code.LANGUAGE_MISMATCH);
        assertCodes(
                validator.validate(turkishRequest, copiedEnglishText),
                OutputValidationIssue.Code.LANGUAGE_MISMATCH);
    }

    @Test
    void rejectsDuplicateCauseAndCheckSelections() {
        StructuredInferenceOutput valid = validOutput();
        StructuredInferenceOutput duplicated =
                new StructuredInferenceOutput(
                        valid.language(),
                        valid.summary(),
                        List.of(valid.causes().getFirst(), valid.causes().getFirst()),
                        List.of(valid.checks().getFirst(), valid.checks().getFirst()));

        assertCodes(
                validator.validate(request(2_000), duplicated),
                OutputValidationIssue.Code.DUPLICATE_SELECTION);
    }

    @Test
    void invalidResultDefensivelyCopiesIssues() {
        OutputValidationResult result =
                validator.validate(
                        request(2_000),
                        new StructuredInferenceOutput(
                                InferenceLanguage.TURKISH, "Hata özeti", List.of(), List.of()));

        assertThat(result)
                .isInstanceOfSatisfying(
                        OutputValidationResult.Invalid.class,
                        invalid -> assertThat(invalid.issues()).isUnmodifiable().isNotEmpty());
    }

    private static void assertCodes(
            OutputValidationResult result, OutputValidationIssue.Code... codes) {
        assertThat(result).isInstanceOf(OutputValidationResult.Invalid.class);
        OutputValidationResult.Invalid invalid = (OutputValidationResult.Invalid) result;
        assertThat(invalid.issues()).extracting(OutputValidationIssue::code).contains(codes);
    }

    private static StructuredInferenceOutput validOutput() {
        return new StructuredInferenceOutput(
                InferenceLanguage.ENGLISH,
                "This failure may come from the database.",
                List.of(
                        new StructuredCause(
                                "cause.one",
                                "This cause is supported by the captured error.",
                                List.of("fact.one"),
                                List.of("source.one"))),
                List.of(
                        new StructuredCheck(
                                "check.one",
                                "Check the database constraint.",
                                List.of("fact.one"),
                                List.of("source.one"))));
    }

    private static InferenceRequest request(int outputLimit) {
        return new InferenceRequest(
                "request.one",
                InferenceLanguage.ENGLISH,
                List.of(
                        new InferenceFact(
                                "fact.one", "Database constraint failed", List.of("source.one"))),
                List.of(
                        new InferenceCauseCandidate(
                                "cause.one",
                                "Constraint",
                                "The constraint rejected data",
                                List.of("fact.one"))),
                List.of(
                        new InferenceCheckCandidate(
                                "check.one", "Inspect the constraint", List.of("fact.one"))),
                List.of(location()),
                outputLimit);
    }

    private static SourceLocation location() {
        return new SourceLocation(
                "source.one", "example.Repository", "save", "Repository.java", 42);
    }
}
