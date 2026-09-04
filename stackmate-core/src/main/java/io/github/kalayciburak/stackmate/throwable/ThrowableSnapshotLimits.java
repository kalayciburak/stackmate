package io.github.kalayciburak.stackmate.throwable;

/**
 * Work and output limits for defensive throwable capture.
 *
 * @param maxCauseDepth maximum number of cause levels to capture
 * @param maxFramesPerThrowable maximum stack frames captured for each throwable
 * @param maxSuppressedPerThrowable maximum suppressed throwables captured for each throwable
 * @param maxTotalNodes maximum cause and suppressed nodes captured in total
 * @param maxMessageLength maximum characters retained from each throwable message
 * @param maxCaptureFailures maximum safe capture-failure descriptions retained
 */
public record ThrowableSnapshotLimits(
        int maxCauseDepth,
        int maxFramesPerThrowable,
        int maxSuppressedPerThrowable,
        int maxTotalNodes,
        int maxMessageLength,
        int maxCaptureFailures) {

    /**
     * Validates and creates defensive capture limits.
     *
     * @param maxCauseDepth maximum number of cause levels to capture
     * @param maxFramesPerThrowable maximum stack frames captured for each throwable
     * @param maxSuppressedPerThrowable maximum suppressed throwables captured for each throwable
     * @param maxTotalNodes maximum cause and suppressed nodes captured in total
     * @param maxMessageLength maximum characters retained from each throwable message
     * @param maxCaptureFailures maximum safe capture-failure descriptions retained
     */
    public ThrowableSnapshotLimits {
        requirePositive(maxCauseDepth, "maxCauseDepth");
        requireNonNegative(maxFramesPerThrowable, "maxFramesPerThrowable");
        requireNonNegative(maxSuppressedPerThrowable, "maxSuppressedPerThrowable");
        requirePositive(maxTotalNodes, "maxTotalNodes");
        requireNonNegative(maxMessageLength, "maxMessageLength");
        requireNonNegative(maxCaptureFailures, "maxCaptureFailures");
    }

    /**
     * Creates the standard defensive capture limits.
     *
     * @return conservative default limits for throwable capture
     */
    public static ThrowableSnapshotLimits defaults() {
        return new ThrowableSnapshotLimits(16, 64, 8, 64, 2_048, 8);
    }

    private static void requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
