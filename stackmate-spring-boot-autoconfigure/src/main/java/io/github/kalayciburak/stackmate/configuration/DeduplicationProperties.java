package io.github.kalayciburak.stackmate.configuration;

import java.time.Duration;

/** Bounds duplicate incident memory and retention. */
public class DeduplicationProperties {

    private boolean enabled = true;
    private Duration ttl = Duration.ofMinutes(10);
    private int maximumEntries = 512;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = PropertyValidation.positive(ttl, "deduplication.ttl");
    }

    public int getMaximumEntries() {
        return maximumEntries;
    }

    public void setMaximumEntries(int maximumEntries) {
        this.maximumEntries =
                PropertyValidation.range(
                        maximumEntries, 1, 1_000_000, "deduplication.maximumEntries");
    }
}
