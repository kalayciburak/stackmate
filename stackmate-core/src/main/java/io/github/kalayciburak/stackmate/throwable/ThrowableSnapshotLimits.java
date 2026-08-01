package io.github.kalayciburak.stackmate.throwable;

/** Work and output limits for defensive throwable capture. */
public record ThrowableSnapshotLimits(
        int maxCauseDepth,
        int maxFramesPerThrowable,
        int maxSuppressedPerThrowable,
        int maxTotalNodes,
        int maxMessageLength,
        int maxCaptureFailures) {

    public ThrowableSnapshotLimits {
        requirePositive(maxCauseDepth, "maxCauseDepth");
        requireNonNegative(maxFramesPerThrowable, "maxFramesPerThrowable");
        requireNonNegative(maxSuppressedPerThrowable, "maxSuppressedPerThrowable");
        requirePositive(maxTotalNodes, "maxTotalNodes");
        requireNonNegative(maxMessageLength, "maxMessageLength");
        requireNonNegative(maxCaptureFailures, "maxCaptureFailures");
    }

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
