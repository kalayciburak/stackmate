package io.github.kalayciburak.stackmate.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import io.github.kalayciburak.stackmate.evidence.EvidenceReliability;
import io.github.kalayciburak.stackmate.evidence.EvidenceSensitivity;
import io.github.kalayciburak.stackmate.evidence.EvidenceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceStrengthCalculatorTest {

    @Test
    void givesHighStrengthOnlyToHighReliabilityStructuredCauseFacts() {
        EvidenceItem exceptionClass =
                evidence(
                        "java.class.0",
                        EvidenceType.EXCEPTION_CLASS,
                        "throwable.nodes[0]",
                        "java.lang.NullPointerException",
                        EvidenceReliability.HIGH);

        assertThat(EvidenceStrengthCalculator.calculate(List.of(exceptionClass)))
                .isEqualTo(DiagnosticStrength.HIGH);
    }

    @Test
    void keepsMessageOnlyPatternAtLowStrength() {
        EvidenceItem pattern =
                evidence(
                        "spring.pattern.0",
                        EvidenceType.MESSAGE_PATTERN,
                        "throwable.nodes[0].message",
                        "required-bean-type",
                        EvidenceReliability.MEDIUM);

        assertThat(EvidenceStrengthCalculator.calculate(List.of(pattern)))
                .isEqualTo(DiagnosticStrength.LOW);
    }

    @Test
    void composesMultipleNonLowFactsIntoMediumStrength() {
        EvidenceItem table =
                evidence(
                        "jdbc.table.0",
                        EvidenceType.DATABASE_TABLE,
                        "throwable.nodes[0].database",
                        "orders",
                        EvidenceReliability.MEDIUM);
        EvidenceItem column =
                evidence(
                        "jdbc.column.0",
                        EvidenceType.DATABASE_COLUMN,
                        "throwable.nodes[0].database",
                        "customer_id",
                        EvidenceReliability.MEDIUM);

        assertThat(EvidenceStrengthCalculator.calculate(List.of(table, column)))
                .isEqualTo(DiagnosticStrength.MEDIUM);
    }

    @Test
    void reportsUnknownWhenThereIsNoEvidence() {
        assertThat(EvidenceStrengthCalculator.calculate(List.of()))
                .isEqualTo(DiagnosticStrength.UNKNOWN);
    }

    private EvidenceItem evidence(
            String id,
            EvidenceType type,
            String source,
            String value,
            EvidenceReliability reliability) {
        return new EvidenceItem(
                id, type, source, value, EvidenceSensitivity.POTENTIALLY_SENSITIVE, reliability);
    }
}
