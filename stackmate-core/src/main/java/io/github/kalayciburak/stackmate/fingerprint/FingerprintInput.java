package io.github.kalayciburak.stackmate.fingerprint;

import java.util.List;
import java.util.Objects;

/**
 * Bounded, framework-neutral input used to identify equivalent incidents.
 *
 * @param analyzerVersion version of the deterministic analyzer contract
 * @param rootType fully qualified type of the root throwable
 * @param category stable diagnostic category
 * @param rootMessage sanitized root throwable message, or {@code null} when unavailable
 * @param topApplicationFrames bounded application-frame signatures
 * @param stableFactTypes stable evidence types included in the fingerprint
 */
public record FingerprintInput(
        String analyzerVersion,
        String rootType,
        String category,
        String rootMessage,
        List<String> topApplicationFrames,
        List<String> stableFactTypes) {

    public static final int MAX_APPLICATION_FRAMES = 16;
    public static final int MAX_STABLE_FACT_TYPES = 32;

    /**
     * Validates and creates bounded fingerprint input.
     *
     * @param analyzerVersion version of the deterministic analyzer contract
     * @param rootType fully qualified type of the root throwable
     * @param category stable diagnostic category
     * @param rootMessage sanitized root throwable message, or {@code null} when unavailable
     * @param topApplicationFrames bounded application-frame signatures
     * @param stableFactTypes stable evidence types included in the fingerprint
     */
    public FingerprintInput {
        Objects.requireNonNull(analyzerVersion, "analyzerVersion");
        Objects.requireNonNull(rootType, "rootType");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(topApplicationFrames, "topApplicationFrames");
        Objects.requireNonNull(stableFactTypes, "stableFactTypes");
        if (topApplicationFrames.size() > MAX_APPLICATION_FRAMES) {
            throw new IllegalArgumentException(
                    "topApplicationFrames exceeds " + MAX_APPLICATION_FRAMES + " entries");
        }
        if (stableFactTypes.size() > MAX_STABLE_FACT_TYPES) {
            throw new IllegalArgumentException(
                    "stableFactTypes exceeds " + MAX_STABLE_FACT_TYPES + " entries");
        }
        topApplicationFrames = List.copyOf(topApplicationFrames);
        stableFactTypes = List.copyOf(stableFactTypes);
    }
}
