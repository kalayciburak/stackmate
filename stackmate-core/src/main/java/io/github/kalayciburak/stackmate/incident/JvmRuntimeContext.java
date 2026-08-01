package io.github.kalayciburak.stackmate.incident;

import java.util.Objects;

/** Small immutable JVM snapshot with no live runtime objects. */
public record JvmRuntimeContext(
        String javaVersion,
        String vmName,
        String vmVendor,
        String osName,
        String osArch,
        int availableProcessors) {

    public JvmRuntimeContext {
        javaVersion = Objects.requireNonNull(javaVersion, "javaVersion");
        vmName = Objects.requireNonNull(vmName, "vmName");
        vmVendor = Objects.requireNonNull(vmVendor, "vmVendor");
        osName = Objects.requireNonNull(osName, "osName");
        osArch = Objects.requireNonNull(osArch, "osArch");
        if (availableProcessors < 1) {
            throw new IllegalArgumentException("availableProcessors must be positive");
        }
    }

    public static JvmRuntimeContext current() {
        return new JvmRuntimeContext(
                property("java.version"),
                property("java.vm.name"),
                property("java.vm.vendor"),
                property("os.name"),
                property("os.arch"),
                Runtime.getRuntime().availableProcessors());
    }

    private static String property(String name) {
        try {
            return System.getProperty(name, "");
        } catch (SecurityException ignored) {
            return "";
        }
    }
}
