package io.github.kalayciburak.stackmate.configuration;

import java.util.ArrayList;
import java.util.List;

/** Defines the explicit opt-in policy for known production profiles. */
public class SafetyProperties {

    private boolean allowInProduction;
    private List<String> productionProfiles = new ArrayList<>(List.of("prod", "production"));

    public boolean isAllowInProduction() {
        return allowInProduction;
    }

    public void setAllowInProduction(boolean allowInProduction) {
        this.allowInProduction = allowInProduction;
    }

    public List<String> getProductionProfiles() {
        return List.copyOf(productionProfiles);
    }

    public void setProductionProfiles(List<String> productionProfiles) {
        this.productionProfiles =
                new ArrayList<>(
                        PropertyValidation.textList(
                                productionProfiles, "safety.productionProfiles", 32, 64));
    }
}
