package io.github.kalayciburak.stackmate.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import io.github.kalayciburak.stackmate.evidence.EvidenceReliability;
import io.github.kalayciburak.stackmate.evidence.EvidenceSensitivity;
import io.github.kalayciburak.stackmate.evidence.EvidenceType;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class DiagnosisAggregatorTest {

    @Test
    void unknownFallbackPreservesAvailableCapturedContextWithoutInventingACause() {
        IncidentSnapshot incident =
                IncidentFixtures.incident(
                        IncidentFixtures.node("example.OuterFailure", "outer sanitized", 0),
                        IncidentFixtures.node("example.UnknownRoot", "root sanitized", 1));

        Diagnosis diagnosis = DefaultDiagnosticAnalyzers.aggregator().diagnose(incident);

        assertThat(diagnosis.fallback()).isTrue();
        assertThat(diagnosis.causes())
                .singleElement()
                .satisfies(
                        cause -> {
                            assertThat(cause.id()).isEqualTo("cause.unknown");
                            assertThat(cause.category()).isEqualTo(DiagnosticCategory.UNKNOWN);
                            assertThat(cause.strength()).isEqualTo(DiagnosticStrength.UNKNOWN);
                            assertThat(cause.explanation()).contains("insufficient");
                        });
        assertThat(diagnosis.evidence())
                .extracting(item -> item.type())
                .contains(
                        EvidenceType.ANALYZER_RESULT,
                        EvidenceType.EXCEPTION_CLASS,
                        EvidenceType.SANITIZED_MESSAGE,
                        EvidenceType.APPLICATION_FRAME,
                        EvidenceType.CAUSE_RELATIONSHIP);
        assertThat(diagnosis.evidence())
                .extracting(item -> item.value())
                .contains(
                        "example.UnknownRoot",
                        "root sanitized",
                        "example.OuterFailure -> example.UnknownRoot");
    }

    @Test
    void executesInOrderExposesVersionsAndFailsOpenWhenAnalyzerThrows() {
        DiagnosticAnalyzer failing =
                new DiagnosticAnalyzer() {
                    @Override
                    public String id() {
                        return "failing";
                    }

                    @Override
                    public String version() {
                        return "2";
                    }

                    @Override
                    public int order() {
                        return 20;
                    }

                    @Override
                    public AnalysisContribution analyze(IncidentSnapshot incident) {
                        throw new IllegalStateException("analyzer failure");
                    }
                };
        DiagnosticAnalyzer empty =
                new DiagnosticAnalyzer() {
                    @Override
                    public String id() {
                        return "empty";
                    }

                    @Override
                    public int order() {
                        return 10;
                    }

                    @Override
                    public AnalysisContribution analyze(IncidentSnapshot incident) {
                        return AnalysisContribution.empty(id());
                    }
                };

        Diagnosis diagnosis =
                new DiagnosisAggregator(java.util.List.of(failing, empty))
                        .diagnose(
                                IncidentFixtures.incident(
                                        IncidentFixtures.node("example.Unknown", "sanitized", 0)));

        assertThat(diagnosis.fallback()).isTrue();
        assertThat(diagnosis.analyzerTokens()).containsExactly("empty@1", "failing@2");
    }

    @Test
    void validatesAnalyzerIdentityAndUniqueness() {
        DiagnosticAnalyzer first =
                analyzer("same", "1", 1, ignored -> AnalysisContribution.empty("same"));
        DiagnosticAnalyzer second =
                analyzer("same", "2", 2, ignored -> AnalysisContribution.empty("same"));
        DiagnosticAnalyzer invalidVersion =
                analyzer(
                        "version",
                        "not valid",
                        1,
                        ignored -> AnalysisContribution.empty("version"));

        assertThatThrownBy(() -> new DiagnosisAggregator(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiagnosisAggregator(List.of(invalidVersion)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void skipsNullMismatchedAndConflictingContributions() {
        EvidenceItem acceptedEvidence = evidence("evidence.shared", "accepted");
        AnalysisContribution accepted =
                contribution(
                        "accepted",
                        acceptedEvidence,
                        "cause.shared",
                        "Accepted",
                        "check.shared",
                        "Check accepted");
        AnalysisContribution conflictingEvidence =
                contribution(
                        "conflict-evidence",
                        evidence("evidence.shared", "different"),
                        "cause.other",
                        "Other",
                        "check.other",
                        "Check other");
        AnalysisContribution conflictingCause =
                contribution(
                        "conflict-cause",
                        evidence("evidence.cause", "cause"),
                        "cause.shared",
                        "Conflicting",
                        "check.cause",
                        "Check cause");
        AnalysisContribution conflictingCheck =
                contribution(
                        "conflict-check",
                        evidence("evidence.check", "check"),
                        "cause.check",
                        "Check cause",
                        "check.shared",
                        "Different instruction");

        Diagnosis diagnosis =
                new DiagnosisAggregator(
                                List.of(
                                        analyzer("accepted", "1", 1, ignored -> accepted),
                                        analyzer("null-result", "1", 2, ignored -> null),
                                        analyzer(
                                                "mismatch",
                                                "1",
                                                3,
                                                ignored -> AnalysisContribution.empty("other")),
                                        analyzer(
                                                "conflict-evidence",
                                                "1",
                                                4,
                                                ignored -> conflictingEvidence),
                                        analyzer(
                                                "conflict-cause",
                                                "1",
                                                5,
                                                ignored -> conflictingCause),
                                        analyzer(
                                                "conflict-check",
                                                "1",
                                                6,
                                                ignored -> conflictingCheck)))
                        .diagnose(
                                IncidentFixtures.incident(
                                        IncidentFixtures.node("example.Unknown", "sanitized", 0)));

        assertThat(diagnosis.fallback()).isFalse();
        assertThat(diagnosis.causes())
                .extracting(DiagnosedCause::id)
                .containsExactly("cause.shared");
        assertThat(diagnosis.evidence())
                .extracting(EvidenceItem::value)
                .containsExactly("accepted");
        assertThat(diagnosis.checks())
                .extracting(CheckCandidate::instruction)
                .containsExactly("Check accepted");
    }

    @Test
    void mergesCompatibleCrossAnalyzerCandidatesAndComposesStrength() {
        AnalysisContribution first =
                contribution(
                        "first",
                        evidence("evidence.first", "first"),
                        "cause.shared",
                        "Shared",
                        "check.shared",
                        "Check shared");
        AnalysisContribution second =
                contribution(
                        "second",
                        evidence("evidence.second", "second"),
                        "cause.shared",
                        "Shared",
                        "check.shared",
                        "Check shared");

        Diagnosis diagnosis =
                new DiagnosisAggregator(
                                List.of(
                                        analyzer("first", "1", 1, ignored -> first),
                                        analyzer("second", "1", 2, ignored -> second)))
                        .diagnose(
                                IncidentFixtures.incident(
                                        IncidentFixtures.node("example.Unknown", "sanitized", 0)));

        assertThat(diagnosis.causes())
                .singleElement()
                .satisfies(
                        cause -> {
                            assertThat(cause.evidenceIds())
                                    .containsExactly("evidence.first", "evidence.second");
                            assertThat(cause.strength()).isEqualTo(DiagnosticStrength.MEDIUM);
                        });
        assertThat(diagnosis.checks().getFirst().evidenceIds())
                .containsExactly("evidence.first", "evidence.second");
    }

    private DiagnosticAnalyzer analyzer(
            String id,
            String version,
            int order,
            Function<IncidentSnapshot, AnalysisContribution> analysis) {
        return new DiagnosticAnalyzer() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String version() {
                return version;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public AnalysisContribution analyze(IncidentSnapshot incident) {
                return analysis.apply(incident);
            }
        };
    }

    private AnalysisContribution contribution(
            String analyzerId,
            EvidenceItem evidence,
            String causeId,
            String causeTitle,
            String checkId,
            String checkInstruction) {
        return new AnalysisContribution(
                analyzerId,
                List.of(evidence),
                List.of(
                        new CauseCandidate(
                                causeId,
                                DiagnosticCategory.GENERAL_JAVA,
                                causeTitle,
                                "Explanation",
                                List.of(evidence.id()))),
                List.of(new CheckCandidate(checkId, checkInstruction, List.of(evidence.id()))));
    }

    private EvidenceItem evidence(String id, String value) {
        return new EvidenceItem(
                id,
                EvidenceType.ANALYZER_RESULT,
                "test." + id,
                value,
                EvidenceSensitivity.PUBLIC,
                EvidenceReliability.MEDIUM);
    }
}
