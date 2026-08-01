package io.github.kalayciburak.stackmate.configuration;

import java.util.Objects;

/**
 * Controls deterministic console presentation without changing application logging configuration.
 */
public class ConsoleProperties {

    /** Selects the renderer layout. */
    public enum Style {
        BOX,
        COMPACT
    }

    /** Selects ANSI emission policy. */
    public enum Ansi {
        AUTO,
        ALWAYS,
        NEVER
    }

    private boolean enabled = true;
    private Style style = Style.BOX;
    private Ansi ansi = Ansi.AUTO;
    private int maximumWidth = 100;
    private boolean showApplicationTrace = true;
    private boolean showTechnicalDetails = true;
    private boolean showCauseChain;
    private boolean debugLogging;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Style getStyle() {
        return style;
    }

    public void setStyle(Style style) {
        this.style = Objects.requireNonNull(style, "style");
    }

    public Ansi getAnsi() {
        return ansi;
    }

    public void setAnsi(Ansi ansi) {
        this.ansi = Objects.requireNonNull(ansi, "ansi");
    }

    public int getMaximumWidth() {
        return maximumWidth;
    }

    public void setMaximumWidth(int maximumWidth) {
        this.maximumWidth = PropertyValidation.range(maximumWidth, 60, 200, "console.maximumWidth");
    }

    public boolean isShowApplicationTrace() {
        return showApplicationTrace;
    }

    public void setShowApplicationTrace(boolean showApplicationTrace) {
        this.showApplicationTrace = showApplicationTrace;
    }

    public boolean isShowTechnicalDetails() {
        return showTechnicalDetails;
    }

    public void setShowTechnicalDetails(boolean showTechnicalDetails) {
        this.showTechnicalDetails = showTechnicalDetails;
    }

    public boolean isShowCauseChain() {
        return showCauseChain;
    }

    public void setShowCauseChain(boolean showCauseChain) {
        this.showCauseChain = showCauseChain;
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }
}
