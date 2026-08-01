package io.github.kalayciburak.stackmate.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Privacy controls; unsupported request-value collection is rejected instead of silently enabled.
 */
public class PrivacyProperties {

    private boolean includeRequestBody;
    private boolean includeQueryValues;
    private boolean includeHeaders;
    private boolean piiRedaction = true;
    private List<String> customRedactionPatterns = new ArrayList<>();

    public boolean isIncludeRequestBody() {
        return includeRequestBody;
    }

    public void setIncludeRequestBody(boolean includeRequestBody) {
        rejectUnavailable(includeRequestBody, "privacy.includeRequestBody");
        this.includeRequestBody = false;
    }

    public boolean isIncludeQueryValues() {
        return includeQueryValues;
    }

    public void setIncludeQueryValues(boolean includeQueryValues) {
        rejectUnavailable(includeQueryValues, "privacy.includeQueryValues");
        this.includeQueryValues = false;
    }

    public boolean isIncludeHeaders() {
        return includeHeaders;
    }

    public void setIncludeHeaders(boolean includeHeaders) {
        rejectUnavailable(includeHeaders, "privacy.includeHeaders");
        this.includeHeaders = false;
    }

    public boolean isPiiRedaction() {
        return piiRedaction;
    }

    public void setPiiRedaction(boolean piiRedaction) {
        this.piiRedaction = piiRedaction;
    }

    public List<String> getCustomRedactionPatterns() {
        return List.copyOf(customRedactionPatterns);
    }

    public void setCustomRedactionPatterns(List<String> customRedactionPatterns) {
        Objects.requireNonNull(customRedactionPatterns, "customRedactionPatterns");
        List<String> validated =
                PropertyValidation.textList(
                        customRedactionPatterns, "privacy.customRedactionPatterns", 32, 1_024);
        for (String expression : validated) {
            try {
                Pattern.compile(expression);
            } catch (PatternSyntaxException failure) {
                throw new IllegalArgumentException(
                        "privacy.customRedactionPatterns contains an invalid regular expression",
                        failure);
            }
        }
        this.customRedactionPatterns = new ArrayList<>(validated);
    }

    private static void rejectUnavailable(boolean value, String name) {
        if (value) {
            throw new IllegalArgumentException(name + " is unavailable in this StackMate version");
        }
    }
}
