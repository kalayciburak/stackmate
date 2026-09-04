package io.github.kalayciburak.stackmate.configuration;

import java.util.ArrayList;
import java.util.List;

/** Controls which supported framework boundaries and correlation fields are observed. */
public class CaptureProperties {

    private boolean mvcUnhandled = true;
    private List<String> selectedMdcKeys =
            new ArrayList<>(List.of("traceId", "spanId", "correlationId"));

    /** Creates capture properties with unhandled MVC capture enabled and default MDC keys. */
    public CaptureProperties() {}

    /**
     * Returns whether exceptions escaping the MVC servlet chain are captured.
     *
     * @return whether unhandled MVC exceptions are captured
     */
    public boolean isMvcUnhandled() {
        return mvcUnhandled;
    }

    /**
     * Sets whether exceptions escaping the MVC servlet chain are captured.
     *
     * @param mvcUnhandled whether unhandled MVC exceptions are captured
     */
    public void setMvcUnhandled(boolean mvcUnhandled) {
        this.mvcUnhandled = mvcUnhandled;
    }

    /**
     * Returns the configured MDC keys whose nonblank values are captured.
     *
     * @return unmodifiable copy of the selected MDC keys
     */
    public List<String> getSelectedMdcKeys() {
        return List.copyOf(selectedMdcKeys);
    }

    /**
     * Replaces the MDC keys selected for capture.
     *
     * @param selectedMdcKeys nonblank keys to capture, limited to 16 entries of 64 characters each
     */
    public void setSelectedMdcKeys(List<String> selectedMdcKeys) {
        this.selectedMdcKeys =
                new ArrayList<>(
                        PropertyValidation.textList(
                                selectedMdcKeys, "capture.selectedMdcKeys", 16, 64));
    }
}
