package io.github.kalayciburak.stackmate.incident;

import java.util.List;
import java.util.Objects;

/** Spring facts supplied by a framework adapter; core itself has no Spring dependency. */
public record SpringRuntimeContext(
        String applicationName,
        List<String> activeProfiles,
        String applicationPackage,
        String bootVersion,
        String frameworkVersion,
        TransactionState transactionState) {

    public SpringRuntimeContext {
        applicationName = Objects.requireNonNull(applicationName, "applicationName");
        activeProfiles = List.copyOf(Objects.requireNonNull(activeProfiles, "activeProfiles"));
        applicationPackage = Objects.requireNonNull(applicationPackage, "applicationPackage");
        bootVersion = Objects.requireNonNull(bootVersion, "bootVersion");
        frameworkVersion = Objects.requireNonNull(frameworkVersion, "frameworkVersion");
        transactionState = Objects.requireNonNull(transactionState, "transactionState");
    }

    public static SpringRuntimeContext unknown() {
        return new SpringRuntimeContext("", List.of(), "", "", "", TransactionState.UNKNOWN);
    }

    public enum TransactionState {
        ACTIVE,
        INACTIVE,
        UNKNOWN
    }
}
