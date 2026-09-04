package io.github.kalayciburak.stackmate.incident;

import java.util.Objects;

/**
 * Small immutable JVM snapshot with no live runtime objects.
 *
 * @param javaVersion value of the {@code java.version} system property
 * @param vmName value of the {@code java.vm.name} system property
 * @param vmVendor value of the {@code java.vm.vendor} system property
 * @param osName value of the {@code os.name} system property
 * @param osArch value of the {@code os.arch} system property
 * @param availableProcessors processor count visible to the JVM
 */
public record JvmRuntimeContext(
        String javaVersion,
        String vmName,
        String vmVendor,
        String osName,
        String osArch,
        int availableProcessors) {

    /**
     * Validates and creates a JVM runtime snapshot.
     *
     * @param javaVersion value of the {@code java.version} system property
     * @param vmName value of the {@code java.vm.name} system property
     * @param vmVendor value of the {@code java.vm.vendor} system property
     * @param osName value of the {@code os.name} system property
     * @param osArch value of the {@code os.arch} system property
     * @param availableProcessors processor count visible to the JVM
     */
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

    /**
     * Captures runtime facts without retaining live runtime objects.
     *
     * @return a snapshot of the JVM and operating system visible to the current process
     */
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
