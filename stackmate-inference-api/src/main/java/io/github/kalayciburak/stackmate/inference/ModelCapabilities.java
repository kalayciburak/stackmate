package io.github.kalayciburak.stackmate.inference;

/** Engine-neutral limits and features advertised by a local model. */
public record ModelCapabilities(
        boolean structuredOutput,
        boolean english,
        boolean turkish,
        int maximumInputCharacters,
        int maximumOutputCharacters) {

    public ModelCapabilities {
        if (maximumInputCharacters < 1 || maximumOutputCharacters < 1) {
            throw new IllegalArgumentException("model character limits must be positive");
        }
    }

    /** Returns whether this model advertises support for the requested language. */
    public boolean supports(InferenceLanguage language) {
        if (language == null) {
            throw new NullPointerException("language");
        }
        return language == InferenceLanguage.ENGLISH ? english : turkish;
    }
}
