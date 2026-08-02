package io.github.kalayciburak.stackmate.benchmark.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.analysis.DiagnosticStrength;
import io.github.kalayciburak.stackmate.inference.InferenceCauseCandidate;
import io.github.kalayciburak.stackmate.inference.InferenceCheckCandidate;
import io.github.kalayciburak.stackmate.inference.InferenceFact;
import io.github.kalayciburak.stackmate.inference.InferenceIncident;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.inference.StructuredInferenceOutput;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExperimentalClaimGroundingValidatorTest {

    private final ExperimentalClaimGroundingValidator validator =
            new ExperimentalClaimGroundingValidator();

    @Test
    void flagsAbstractUngroundedAlternativeAndQuantifiedCausalClaims() {
        assertThat(
                        validator.hasUnsupportedClaim(
                                request(), output("Duplicate value or a driver problem.")))
                .isTrue();
        assertThat(
                        validator.hasUnsupportedClaim(
                                request(), output("The failure was due to a high retry rate.")))
                .isTrue();
    }

    @Test
    void allowsAlternativeWordsPresentInSelectedCauseText() {
        assertThat(
                        validator.hasUnsupportedClaim(
                                request(),
                                output("A protected value or unique value already exists.")))
                .isFalse();
    }

    private StructuredInferenceOutput output(String explanation) {
        return new StructuredInferenceOutput(
                "2",
                "The protected value already exists.",
                explanation,
                List.of("cause.one"),
                List.of("check.one"),
                List.of("fact.one", "fact.two"),
                List.of());
    }

    private InferenceRequest request() {
        return new InferenceRequest(
                "2",
                "2",
                "request.one",
                new InferenceIncident(
                        DiagnosticCategory.DATABASE_CONSTRAINT, DiagnosticStrength.HIGH),
                List.of(
                        new InferenceFact("fact.one", "CONSTRAINT", "uk_user_email"),
                        new InferenceFact("fact.two", "SQL_STATE", "23505")),
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
                List.of(),
                2_000);
    }
}
