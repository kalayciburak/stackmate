package io.github.kalayciburak.stackmate.inference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.analysis.DiagnosticStrength;
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
    void returnsTrustedOutputWithSelectionsResolvedToDeterministicCandidates() {
        OutputValidationResult result = validator.validate(request(2_000), validOutput());

        assertThat(result)
                .isInstanceOfSatisfying(
                        OutputValidationResult.Valid.class,
                        valid -> {
                            assertThat(valid.output().summary())
                                    .isEqualTo(
                                            "The insert failed because the value already exists.");
                            assertThat(valid.output().selectedCauses())
                                    .extracting(InferenceCauseCandidate::id)
                                    .containsExactly("cause.one");
                            assertThat(valid.output().selectedChecks())
                                    .extracting(InferenceCheckCandidate::id)
                                    .containsExactly("check.one");
                            assertThat(valid.output().usedEvidenceIds())
                                    .containsExactly("fact.one");
                        });
    }

    @Test
    void rejectsUnknownSchemaEvidenceCauseAndCheckIds() {
        StructuredInferenceOutput output =
                new StructuredInferenceOutput(
                        "99",
                        "This is an error summary.",
                        "This explanation uses only the supplied error.",
                        List.of("cause.unknown"),
                        List.of("check.unknown"),
                        List.of("fact.unknown"),
                        List.of());

        assertCodes(
                validator.validate(request(2_000), output),
                OutputValidationIssue.Code.UNKNOWN_SCHEMA_VERSION,
                OutputValidationIssue.Code.UNKNOWN_CAUSE_ID,
                OutputValidationIssue.Code.UNKNOWN_CHECK_ID,
                OutputValidationIssue.Code.UNKNOWN_FACT_ID);
    }

    @Test
    void rejectsSelectedCandidateWithoutUsedSupportingEvidence() {
        StructuredInferenceOutput output =
                new StructuredInferenceOutput(
                        "2",
                        "This is the failure summary.",
                        "This explanation uses another supplied fact.",
                        List.of("cause.one"),
                        List.of(),
                        List.of("fact.location"),
                        List.of());

        assertCodes(
                validator.validate(request(2_000), output),
                OutputValidationIssue.Code.UNSUPPORTED_REFERENCE);
    }

    @Test
    void rejectsExcessiveOutputAndUnsafeControlCharacters() {
        StructuredInferenceOutput output =
                new StructuredInferenceOutput(
                        "2",
                        "This error has an escape \u001Bsequence.",
                        "The operation failed with the supplied evidence.",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        assertCodes(
                validator.validate(request(10), output),
                OutputValidationIssue.Code.EXCESSIVE_LENGTH,
                OutputValidationIssue.Code.CONTROL_CHARACTER);
    }

    @Test
    void rejectsForbiddenClaimsNumericConfidenceAndSourceInspectionClaims() {
        StructuredInferenceOutput output =
                new StructuredInferenceOutput(
                        "2",
                        "This is the guaranteed root cause with confidence: 95.",
                        "The source code was inspected for this failure.",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        assertCodes(
                validator.validate(request(2_000), output),
                OutputValidationIssue.Code.FORBIDDEN_CLAIM);
    }

    @Test
    void rejectsSensitiveValuesFabricatedSourcesAndTechnicalIdentifiers() {
        StructuredInferenceOutput output =
                new StructuredInferenceOutput(
                        "2",
                        "Inspect InventedSource.java:42 and uk_attacker_secret.",
                        "This error contains password=hunter2.",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        assertCodes(
                validator.validate(request(2_000), output),
                OutputValidationIssue.Code.SENSITIVE_VALUE,
                OutputValidationIssue.Code.FABRICATED_SOURCE_REFERENCE,
                OutputValidationIssue.Code.FABRICATED_IDENTIFIER);
    }

    @Test
    void rejectsMarkdownFencesAndDestructiveCommands() {
        StructuredInferenceOutput output =
                new StructuredInferenceOutput(
                        "2",
                        "```json is not allowed.",
                        "Run rm -rf / to resolve this error.",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        assertCodes(
                validator.validate(request(2_000), output),
                OutputValidationIssue.Code.MARKDOWN_FENCE,
                OutputValidationIssue.Code.DESTRUCTIVE_COMMAND);
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
                        "2",
                        "This error contains CUSTOM-4821.",
                        "The operation failed with the supplied evidence.",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        assertCodes(
                configuredValidator.validate(request(2_000), output),
                OutputValidationIssue.Code.SENSITIVE_VALUE);
    }

    @Test
    void acceptsSourceAndTechnicalIdentifiersOnlyWhenSuppliedAsFacts() {
        StructuredInferenceOutput output =
                new StructuredInferenceOutput(
                        "2",
                        "The failure is visible at Repository.java:42.",
                        "The database enforced uk_user_email for this value.",
                        List.of("cause.one"),
                        List.of("check.one"),
                        List.of("fact.one", "fact.location"),
                        List.of());

        assertThat(validator.validate(request(2_000), output))
                .isInstanceOf(OutputValidationResult.Valid.class);
    }

    @Test
    void rejectsOutputThatDoesNotAppearToBeEnglish() {
        StructuredInferenceOutput nonEnglish =
                new StructuredInferenceOutput(
                        "2",
                        "Bu hata çünkü neden olabilir.",
                        "Bu işlem için değeri kontrol edin.",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        assertCodes(
                validator.validate(request(2_000), nonEnglish),
                OutputValidationIssue.Code.OUTPUT_NOT_ENGLISH);

        StructuredInferenceOutput mixedScript =
                new StructuredInferenceOutput(
                        "2",
                        "The available evidence is too通用 for a specific cause.",
                        "This operation needs more runtime evidence.",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        assertCodes(
                validator.validate(request(2_000), mixedScript),
                OutputValidationIssue.Code.OUTPUT_NOT_ENGLISH);
    }

    @Test
    void rejectsDuplicateSelectionsAtTheUntrustedOutputBoundary() {
        assertThatThrownBy(
                        () ->
                                new StructuredInferenceOutput(
                                        "2",
                                        "The insert failed because the value already exists.",
                                        "The database enforced the supplied constraint.",
                                        List.of("cause.one", "cause.one"),
                                        List.of(),
                                        List.of("fact.one"),
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be unique");
    }

    @Test
    void invalidResultDefensivelyCopiesIssues() {
        OutputValidationResult result =
                validator.validate(
                        request(2_000),
                        new StructuredInferenceOutput(
                                "99",
                                "Hata özeti.",
                                "Bu hata için kanıt yetersiz.",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()));

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
                "2",
                "The insert failed because the value already exists.",
                "The database enforced uk_user_email for the supplied value.",
                List.of("cause.one"),
                List.of("check.one"),
                List.of("fact.one"),
                List.of());
    }

    private static InferenceRequest request(int outputLimit) {
        return new InferenceRequest(
                InferenceRequest.SCHEMA_VERSION,
                InferenceRequest.PROMPT_VERSION,
                "request.one",
                new InferenceIncident(
                        DiagnosticCategory.DATABASE_CONSTRAINT, DiagnosticStrength.HIGH),
                List.of(
                        new InferenceFact("fact.one", "CONSTRAINT", "uk_user_email"),
                        new InferenceFact("fact.two", "SQL_STATE", "23505"),
                        new InferenceFact(
                                "fact.location",
                                "APPLICATION_LOCATION",
                                "Repository#save(Repository.java:42)")),
                List.of(
                        new InferenceCauseCandidate(
                                "cause.one",
                                "A protected unique value already exists",
                                List.of("fact.one", "fact.two"))),
                List.of(
                        new InferenceCheckCandidate(
                                "check.one",
                                "Check whether the value already exists",
                                List.of("fact.one", "fact.two"))),
                List.of(
                        new InferenceTerminology(
                                "unique constraint",
                                "A database rule that prevents duplicate values")),
                outputLimit);
    }
}
