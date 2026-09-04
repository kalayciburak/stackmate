package io.github.kalayciburak.stackmate.incident;

import java.util.List;
import java.util.Objects;

/**
 * Spring facts supplied by a framework adapter; core itself has no Spring dependency.
 *
 * @param applicationName configured application name, or an empty string when unavailable
 * @param activeProfiles active Spring profile names
 * @param applicationPackage application base package, or an empty string when unavailable
 * @param bootVersion Spring Boot version, or an empty string when unavailable
 * @param frameworkVersion Spring Framework version, or an empty string when unavailable
 * @param transactionState transaction activity observed during capture
 */
public record SpringRuntimeContext(
        String applicationName,
        List<String> activeProfiles,
        String applicationPackage,
        String bootVersion,
        String frameworkVersion,
        TransactionState transactionState) {

    /**
     * Validates and creates a Spring runtime context snapshot.
     *
     * @param applicationName configured application name, or an empty string when unavailable
     * @param activeProfiles active Spring profile names
     * @param applicationPackage application base package, or an empty string when unavailable
     * @param bootVersion Spring Boot version, or an empty string when unavailable
     * @param frameworkVersion Spring Framework version, or an empty string when unavailable
     * @param transactionState transaction activity observed during capture
     */
    public SpringRuntimeContext {
        applicationName = Objects.requireNonNull(applicationName, "applicationName");
        activeProfiles = List.copyOf(Objects.requireNonNull(activeProfiles, "activeProfiles"));
        applicationPackage = Objects.requireNonNull(applicationPackage, "applicationPackage");
        bootVersion = Objects.requireNonNull(bootVersion, "bootVersion");
        frameworkVersion = Objects.requireNonNull(frameworkVersion, "frameworkVersion");
        transactionState = Objects.requireNonNull(transactionState, "transactionState");
    }

    /**
     * Creates a Spring context with no available framework facts.
     *
     * @return an empty Spring context whose transaction state is unknown
     */
    public static SpringRuntimeContext unknown() {
        return new SpringRuntimeContext("", List.of(), "", "", "", TransactionState.UNKNOWN);
    }

    /** Transaction activity reported by the Spring adapter. */
    public enum TransactionState {
        /** A transaction was active during capture. */
        ACTIVE,

        /** No transaction was active during capture. */
        INACTIVE,

        /** Transaction activity could not be determined. */
        UNKNOWN
    }
}
