package io.github.kalayciburak.stackmate.configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.core.env.Environment;

/** Evaluates the production safety policy once so callers can log at most one warning. */
public final class ProductionProfileGuard {

    private final Decision decision;

    /**
     * Evaluates the active profiles against the configured production safety policy.
     *
     * @param environment source of active Spring profiles
     * @param properties StackMate safety configuration
     */
    public ProductionProfileGuard(Environment environment, StackMateProperties properties) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(properties, "properties");
        this.decision = evaluate(environment, properties);
    }

    /**
     * Returns the production-profile decision computed during construction.
     *
     * @return precomputed safety decision
     */
    public Decision decision() {
        return decision;
    }

    private static Decision evaluate(Environment environment, StackMateProperties properties) {
        Set<String> knownProductionProfiles = new HashSet<>();
        for (String profile : properties.getSafety().getProductionProfiles()) {
            knownProductionProfiles.add(profile.toLowerCase(Locale.ROOT));
        }
        List<String> matches =
                Arrays.stream(environment.getActiveProfiles())
                        .filter(
                                profile ->
                                        knownProductionProfiles.contains(
                                                profile.toLowerCase(Locale.ROOT)))
                        .distinct()
                        .sorted()
                        .toList();
        boolean blocked = !matches.isEmpty() && !properties.getSafety().isAllowInProduction();
        return new Decision(!blocked, blocked, matches);
    }

    /**
     * Immutable result of evaluating active profiles against the production safety settings.
     *
     * @param analysisAllowed whether incident analysis may run
     * @param warningRequired whether callers should report that analysis was blocked
     * @param matchedProductionProfiles active profiles that matched configured production names
     */
    public record Decision(
            boolean analysisAllowed,
            boolean warningRequired,
            List<String> matchedProductionProfiles) {

        /** Copies the matched profile list into an unmodifiable list. */
        public Decision {
            matchedProductionProfiles = List.copyOf(matchedProductionProfiles);
        }
    }
}
