package io.github.kalayciburak.stackmate.configuration;

/** Bounds deterministic throwable and application-frame extraction work. */
public class DiagnosticsProperties {

    private int maximumCauseDepth = 32;
    private int maximumSuppressedExceptions = 8;
    private int maximumStackFrames = 120;
    private int maximumApplicationFrames = 8;
    private int maximumMessageLength = 2_000;

    public int getMaximumCauseDepth() {
        return maximumCauseDepth;
    }

    public void setMaximumCauseDepth(int maximumCauseDepth) {
        this.maximumCauseDepth =
                PropertyValidation.range(
                        maximumCauseDepth, 1, 256, "diagnostics.maximumCauseDepth");
    }

    public int getMaximumSuppressedExceptions() {
        return maximumSuppressedExceptions;
    }

    public void setMaximumSuppressedExceptions(int maximumSuppressedExceptions) {
        this.maximumSuppressedExceptions =
                PropertyValidation.range(
                        maximumSuppressedExceptions,
                        0,
                        256,
                        "diagnostics.maximumSuppressedExceptions");
    }

    public int getMaximumStackFrames() {
        return maximumStackFrames;
    }

    public void setMaximumStackFrames(int maximumStackFrames) {
        this.maximumStackFrames =
                PropertyValidation.range(
                        maximumStackFrames, 0, 10_000, "diagnostics.maximumStackFrames");
    }

    public int getMaximumApplicationFrames() {
        return maximumApplicationFrames;
    }

    public void setMaximumApplicationFrames(int maximumApplicationFrames) {
        this.maximumApplicationFrames =
                PropertyValidation.range(
                        maximumApplicationFrames, 1, 128, "diagnostics.maximumApplicationFrames");
    }

    public int getMaximumMessageLength() {
        return maximumMessageLength;
    }

    public void setMaximumMessageLength(int maximumMessageLength) {
        this.maximumMessageLength =
                PropertyValidation.range(
                        maximumMessageLength, 1, 100_000, "diagnostics.maximumMessageLength");
    }
}
