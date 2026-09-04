package io.github.kalayciburak.stackmate.configuration;

/** Bounds deterministic throwable and application-frame extraction work. */
public class DiagnosticsProperties {

    private int maximumCauseDepth = 32;
    private int maximumSuppressedExceptions = 8;
    private int maximumStackFrames = 120;
    private int maximumApplicationFrames = 8;
    private int maximumMessageLength = 2_000;

    /** Creates diagnostic bounds with the default capture limits. */
    public DiagnosticsProperties() {}

    /**
     * Returns the maximum number of throwable cause levels captured.
     *
     * @return maximum cause depth
     */
    public int getMaximumCauseDepth() {
        return maximumCauseDepth;
    }

    /**
     * Sets the maximum number of throwable cause levels captured.
     *
     * @param maximumCauseDepth cause depth from 1 through 64
     */
    public void setMaximumCauseDepth(int maximumCauseDepth) {
        this.maximumCauseDepth =
                PropertyValidation.range(maximumCauseDepth, 1, 64, "diagnostics.maximumCauseDepth");
    }

    /**
     * Returns the maximum number of suppressed exceptions captured per throwable.
     *
     * @return maximum suppressed exceptions per throwable
     */
    public int getMaximumSuppressedExceptions() {
        return maximumSuppressedExceptions;
    }

    /**
     * Sets the maximum number of suppressed exceptions captured per throwable.
     *
     * @param maximumSuppressedExceptions per-throwable bound from 0 through 16
     */
    public void setMaximumSuppressedExceptions(int maximumSuppressedExceptions) {
        this.maximumSuppressedExceptions =
                PropertyValidation.range(
                        maximumSuppressedExceptions,
                        0,
                        16,
                        "diagnostics.maximumSuppressedExceptions");
    }

    /**
     * Returns the maximum number of stack frames captured per throwable.
     *
     * @return maximum stack frames per throwable
     */
    public int getMaximumStackFrames() {
        return maximumStackFrames;
    }

    /**
     * Sets the maximum number of stack frames captured per throwable.
     *
     * @param maximumStackFrames per-throwable bound from 0 through 256
     */
    public void setMaximumStackFrames(int maximumStackFrames) {
        this.maximumStackFrames =
                PropertyValidation.range(
                        maximumStackFrames, 0, 256, "diagnostics.maximumStackFrames");
    }

    /**
     * Returns the maximum number of selected application frames.
     *
     * @return maximum selected application frames
     */
    public int getMaximumApplicationFrames() {
        return maximumApplicationFrames;
    }

    /**
     * Sets the maximum number of selected application frames.
     *
     * @param maximumApplicationFrames frame bound from 1 through 32
     */
    public void setMaximumApplicationFrames(int maximumApplicationFrames) {
        this.maximumApplicationFrames =
                PropertyValidation.range(
                        maximumApplicationFrames, 1, 32, "diagnostics.maximumApplicationFrames");
    }

    /**
     * Returns the maximum captured throwable message length.
     *
     * @return maximum message length in characters
     */
    public int getMaximumMessageLength() {
        return maximumMessageLength;
    }

    /**
     * Sets the maximum captured throwable message length.
     *
     * @param maximumMessageLength character bound from 1 through 4,096
     */
    public void setMaximumMessageLength(int maximumMessageLength) {
        this.maximumMessageLength =
                PropertyValidation.range(
                        maximumMessageLength, 1, 4_096, "diagnostics.maximumMessageLength");
    }
}
