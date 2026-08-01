package io.github.kalayciburak.stackmate.output;

import java.util.Objects;

/** Immutable, per-call console rendering options. */
public record RenderOptions(
        RenderLanguage language,
        RenderStyle style,
        int maxWidth,
        boolean showApplicationTrace,
        boolean showDetails,
        boolean showCauseChain) {

    public static final int MIN_WIDTH = 60;
    public static final int MAX_WIDTH = 200;

    public RenderOptions {
        language = Objects.requireNonNull(language, "language");
        style = Objects.requireNonNull(style, "style");
        if (maxWidth < MIN_WIDTH || maxWidth > MAX_WIDTH) {
            throw new IllegalArgumentException("maxWidth must be between 60 and 200");
        }
    }

    public static RenderOptions defaults() {
        return new RenderOptions(RenderLanguage.ENGLISH, RenderStyle.BOX, 100, true, false, false);
    }
}
