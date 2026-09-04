package io.github.kalayciburak.stackmate.output;

/** Plain-terminal presentation styles. Neither style emits ANSI control sequences. */
public enum RenderStyle {
    /** Renders content inside a fixed-width plain-text border. */
    BOX,

    /** Renders content as unbordered plain-text lines. */
    COMPACT
}
