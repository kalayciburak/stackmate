package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import io.github.kalayciburak.stackmate.evidence.EvidenceReliability;
import io.github.kalayciburak.stackmate.evidence.EvidenceType;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Computes diagnosis strength exclusively from deterministic evidence properties. */
public final class EvidenceStrengthCalculator {

    private EvidenceStrengthCalculator() {}

    public static DiagnosticStrength calculate(Collection<EvidenceItem> evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.isEmpty()) {
            return DiagnosticStrength.UNKNOWN;
        }

        boolean hasHighStructuredFact = false;
        int usableFacts = 0;
        Set<String> independentSources = new HashSet<>();
        for (EvidenceItem item : evidence) {
            Objects.requireNonNull(item, "evidence item");
            if (item.reliability() != EvidenceReliability.LOW) {
                usableFacts++;
                independentSources.add(item.source());
            }
            if (item.reliability() == EvidenceReliability.HIGH
                    && isStructuredCauseFact(item.type())) {
                hasHighStructuredFact = true;
            }
        }

        if (hasHighStructuredFact) {
            return DiagnosticStrength.HIGH;
        }
        if (usableFacts >= 2 || independentSources.size() >= 2) {
            return DiagnosticStrength.MEDIUM;
        }
        return DiagnosticStrength.LOW;
    }

    private static boolean isStructuredCauseFact(EvidenceType type) {
        return switch (type) {
            case EXCEPTION_CLASS, SQL_STATE, DATABASE_CONSTRAINT -> true;
            case MESSAGE_PATTERN,
                    DATABASE_VENDOR_CODE,
                    DATABASE_TABLE,
                    DATABASE_COLUMN,
                    WRAPPER_CLASS,
                    SANITIZED_MESSAGE,
                    APPLICATION_FRAME,
                    CAUSE_RELATIONSHIP,
                    ANALYZER_RESULT ->
                    false;
        };
    }
}
