package io.github.kalayciburak.stackmate.configuration;

import java.time.Duration;

/** Bounds duplicate incident memory and retention. */
public class DeduplicationProperties {

    private boolean enabled = true;
    private Duration ttl = Duration.ofMinutes(10);
    private int maximumEntries = 512;

    /** Creates deduplication properties with a ten-minute TTL and 512-entry bound. */
    public DeduplicationProperties() {}

    /**
     * Returns whether repeated fingerprints are deduplicated.
     *
     * @return whether deduplication is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether repeated fingerprints are deduplicated.
     *
     * @param enabled whether deduplication is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns how long a fingerprint remains in the deduplication window.
     *
     * @return fingerprint retention duration
     */
    public Duration getTtl() {
        return ttl;
    }

    /**
     * Sets how long a fingerprint remains in the deduplication window.
     *
     * @param ttl positive duration no longer than one day
     */
    public void setTtl(Duration ttl) {
        Duration validated = PropertyValidation.positive(ttl, "deduplication.ttl");
        if (validated.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("deduplication.ttl must not exceed one day");
        }
        this.ttl = validated;
    }

    /**
     * Returns the maximum number of fingerprints retained in memory.
     *
     * @return maximum retained fingerprint count
     */
    public int getMaximumEntries() {
        return maximumEntries;
    }

    /**
     * Sets the maximum number of fingerprints retained in memory.
     *
     * @param maximumEntries entry bound from 1 through 10,000
     */
    public void setMaximumEntries(int maximumEntries) {
        this.maximumEntries =
                PropertyValidation.range(maximumEntries, 1, 10_000, "deduplication.maximumEntries");
    }
}
