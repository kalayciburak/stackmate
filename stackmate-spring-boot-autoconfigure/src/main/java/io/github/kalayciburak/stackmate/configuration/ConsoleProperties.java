package io.github.kalayciburak.stackmate.configuration;

import java.util.Objects;

/**
 * Controls deterministic console presentation without changing application logging configuration.
 */
public class ConsoleProperties {

    /** Selects the renderer layout. */
    public enum Style {
        /** Renders content inside a fixed-width plain-text border. */
        BOX,

        /** Renders content as unbordered plain-text lines. */
        COMPACT
    }

    private boolean enabled = true;
    private Style style = Style.BOX;
    private int maximumWidth = 100;
    private boolean showApplicationTrace = true;
    private boolean showTechnicalDetails = true;
    private boolean showCauseChain;
    private boolean debugLogging;

    /** Creates console properties with the default bounded presentation settings. */
    public ConsoleProperties() {}

    /**
     * Returns whether StackMate writes diagnostic messages to its console logger.
     *
     * @return whether console output is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether StackMate writes diagnostic messages to its console logger.
     *
     * @param enabled whether console output is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the configured console layout.
     *
     * @return configured layout
     */
    public Style getStyle() {
        return style;
    }

    /**
     * Sets the console layout.
     *
     * @param style non-null layout to use
     */
    public void setStyle(Style style) {
        this.style = Objects.requireNonNull(style, "style");
    }

    /**
     * Returns the maximum rendered line width.
     *
     * @return maximum width in characters
     */
    public int getMaximumWidth() {
        return maximumWidth;
    }

    /**
     * Sets the maximum rendered line width.
     *
     * @param maximumWidth width from 60 through 200 characters
     */
    public void setMaximumWidth(int maximumWidth) {
        this.maximumWidth = PropertyValidation.range(maximumWidth, 60, 200, "console.maximumWidth");
    }

    /**
     * Returns whether selected application stack frames are included.
     *
     * @return whether the application trace is included
     */
    public boolean isShowApplicationTrace() {
        return showApplicationTrace;
    }

    /**
     * Sets whether selected application stack frames are included.
     *
     * @param showApplicationTrace whether to include the application trace
     */
    public void setShowApplicationTrace(boolean showApplicationTrace) {
        this.showApplicationTrace = showApplicationTrace;
    }

    /**
     * Returns whether the cause explanation and request summary are included.
     *
     * @return whether technical details are included
     */
    public boolean isShowTechnicalDetails() {
        return showTechnicalDetails;
    }

    /**
     * Sets whether the cause explanation and request summary are included.
     *
     * @param showTechnicalDetails whether to include technical details
     */
    public void setShowTechnicalDetails(boolean showTechnicalDetails) {
        this.showTechnicalDetails = showTechnicalDetails;
    }

    /**
     * Returns whether the captured throwable cause chain is included.
     *
     * @return whether the cause chain is included
     */
    public boolean isShowCauseChain() {
        return showCauseChain;
    }

    /**
     * Sets whether the captured throwable cause chain is included.
     *
     * @param showCauseChain whether to include the cause chain
     */
    public void setShowCauseChain(boolean showCauseChain) {
        this.showCauseChain = showCauseChain;
    }

    /**
     * Returns whether fail-open internal failures are logged at debug level.
     *
     * @return whether internal failures are debug-logged
     */
    public boolean isDebugLogging() {
        return debugLogging;
    }

    /**
     * Sets whether fail-open internal failures are logged at debug level.
     *
     * @param debugLogging whether internal failures are debug-logged
     */
    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }
}
