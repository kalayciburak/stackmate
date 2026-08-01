package io.github.kalayciburak.stackmate.configuration;

import java.util.ArrayList;
import java.util.List;

/** Controls which supported framework boundaries and correlation fields are observed. */
public class CaptureProperties {

    private boolean mvcUnhandled = true;
    private List<String> selectedMdcKeys =
            new ArrayList<>(List.of("traceId", "spanId", "correlationId"));

    public boolean isMvcUnhandled() {
        return mvcUnhandled;
    }

    public void setMvcUnhandled(boolean mvcUnhandled) {
        this.mvcUnhandled = mvcUnhandled;
    }

    public List<String> getSelectedMdcKeys() {
        return List.copyOf(selectedMdcKeys);
    }

    public void setSelectedMdcKeys(List<String> selectedMdcKeys) {
        this.selectedMdcKeys =
                new ArrayList<>(
                        PropertyValidation.textList(
                                selectedMdcKeys, "capture.selectedMdcKeys", 16, 64));
    }
}
