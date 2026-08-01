package io.github.kalayciburak.stackmate.benchmark.corpus;

import io.github.kalayciburak.stackmate.evidence.EvidenceType;
import java.util.Objects;

/** A stable evidence type/value pair required by benchmark ground truth. */
public record ExpectedEvidenceFact(EvidenceType type, String value) {

    public ExpectedEvidenceFact {
        type = Objects.requireNonNull(type, "type");
        value = Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
