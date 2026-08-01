package io.github.kalayciburak.stackmate.inference;

/** Engine-neutral limits and features advertised by a local model. */
public record ModelCapabilities(
        boolean structuredOutput, int maximumInputCharacters, int maximumOutputCharacters) {

    public ModelCapabilities {
        if (maximumInputCharacters < 1 || maximumOutputCharacters < 1) {
            throw new IllegalArgumentException("model character limits must be positive");
        }
    }
}
