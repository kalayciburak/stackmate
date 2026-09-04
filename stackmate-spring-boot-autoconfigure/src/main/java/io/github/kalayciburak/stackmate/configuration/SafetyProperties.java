package io.github.kalayciburak.stackmate.configuration;

import java.util.ArrayList;
import java.util.List;

/** Defines the explicit opt-in policy for known production profiles. */
public class SafetyProperties {

    private boolean allowInProduction;
    private List<String> productionProfiles = new ArrayList<>(List.of("prod", "production"));

    /** Creates safety properties that block analysis for the default production profiles. */
    public SafetyProperties() {}

    /**
     * Returns whether analysis is explicitly allowed under a matched production profile.
     *
     * @return whether production-profile analysis is allowed
     */
    public boolean isAllowInProduction() {
        return allowInProduction;
    }

    /**
     * Sets whether analysis is explicitly allowed under a matched production profile.
     *
     * @param allowInProduction whether production-profile analysis is allowed
     */
    public void setAllowInProduction(boolean allowInProduction) {
        this.allowInProduction = allowInProduction;
    }

    /**
     * Returns the profile names treated as production profiles.
     *
     * @return unmodifiable copy of configured production profile names
     */
    public List<String> getProductionProfiles() {
        return List.copyOf(productionProfiles);
    }

    /**
     * Replaces the profile names treated as production profiles.
     *
     * @param productionProfiles up to 32 nonblank names of at most 64 characters each
     */
    public void setProductionProfiles(List<String> productionProfiles) {
        this.productionProfiles =
                new ArrayList<>(
                        PropertyValidation.textList(
                                productionProfiles, "safety.productionProfiles", 32, 64));
    }
}
