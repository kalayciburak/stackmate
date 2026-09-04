package io.github.kalayciburak.stackmate.output;

import java.util.Objects;

/**
 * Immutable, per-call console rendering options.
 *
 * @param style bordered or compact plain-text layout
 * @param maxWidth maximum rendered line width from 60 through 200 characters
 * @param showApplicationTrace whether selected application frames are included
 * @param showDetails whether the cause explanation and request summary are included
 * @param showCauseChain whether the captured throwable cause chain is included
 */
public record RenderOptions(
        RenderStyle style,
        int maxWidth,
        boolean showApplicationTrace,
        boolean showDetails,
        boolean showCauseChain) {

    /** Smallest accepted rendered line width. */
    public static final int MIN_WIDTH = 60;

    /** Largest accepted rendered line width. */
    public static final int MAX_WIDTH = 200;

    /**
     * Validates and creates rendering options.
     *
     * @param style bordered or compact plain-text layout
     * @param maxWidth maximum rendered line width from 60 through 200 characters
     * @param showApplicationTrace whether selected application frames are included
     * @param showDetails whether the cause explanation and request summary are included
     * @param showCauseChain whether the captured throwable cause chain is included
     */
    public RenderOptions {
        style = Objects.requireNonNull(style, "style");
        if (maxWidth < MIN_WIDTH || maxWidth > MAX_WIDTH) {
            throw new IllegalArgumentException("maxWidth must be between 60 and 200");
        }
    }

    /**
     * Returns the default boxed 100-column options with only application frames enabled.
     *
     * @return default rendering options
     */
    public static RenderOptions defaults() {
        return new RenderOptions(RenderStyle.BOX, 100, true, false, false);
    }
}
