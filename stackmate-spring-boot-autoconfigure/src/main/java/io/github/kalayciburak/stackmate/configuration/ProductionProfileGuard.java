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

    public ProductionProfileGuard(Environment environment, StackMateProperties properties) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(properties, "properties");
        this.decision = evaluate(environment, properties);
    }

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

    /** Immutable startup decision; {@code warningRequired} is intended for one-time logging. */
    public record Decision(
            boolean analysisAllowed,
            boolean warningRequired,
            List<String> matchedProductionProfiles) {

        public Decision {
            matchedProductionProfiles = List.copyOf(matchedProductionProfiles);
        }
    }
}
