package io.github.kalayciburak.stackmate.incident;

import java.util.Objects;

/**
 * An immutable, serialization-safe copy of one stack frame.
 *
 * @param className declaring class name
 * @param methodName method name
 * @param fileName source file name, or an empty string when unavailable
 * @param lineNumber source line number as reported by the JVM
 * @param nativeMethod whether the frame represents a native method
 * @param moduleName module name, or an empty string when unavailable
 * @param moduleVersion module version, or an empty string when unavailable
 * @param classLoaderName class-loader name, or an empty string when unavailable
 * @param causalDepth zero-based depth of the owning throwable in the causal chain
 */
public record StackFrameSnapshot(
        String className,
        String methodName,
        String fileName,
        int lineNumber,
        boolean nativeMethod,
        String moduleName,
        String moduleVersion,
        String classLoaderName,
        int causalDepth) {

    /**
     * Validates and creates a stack-frame snapshot.
     *
     * @param className declaring class name
     * @param methodName method name
     * @param fileName source file name, or an empty string when unavailable
     * @param lineNumber source line number as reported by the JVM
     * @param nativeMethod whether the frame represents a native method
     * @param moduleName module name, or an empty string when unavailable
     * @param moduleVersion module version, or an empty string when unavailable
     * @param classLoaderName class-loader name, or an empty string when unavailable
     * @param causalDepth zero-based depth of the owning throwable in the causal chain
     */
    public StackFrameSnapshot {
        className = Objects.requireNonNull(className, "className");
        methodName = Objects.requireNonNull(methodName, "methodName");
        fileName = Objects.requireNonNull(fileName, "fileName");
        moduleName = Objects.requireNonNull(moduleName, "moduleName");
        moduleVersion = Objects.requireNonNull(moduleVersion, "moduleVersion");
        classLoaderName = Objects.requireNonNull(classLoaderName, "classLoaderName");
        if (causalDepth < 0) {
            throw new IllegalArgumentException("causalDepth must not be negative");
        }
    }

    /**
     * Copies a live JVM stack-frame element into serialization-safe data.
     *
     * @param frame JVM stack-frame element to copy
     * @param causalDepth zero-based depth of the owning throwable
     * @return immutable stack-frame snapshot
     */
    public static StackFrameSnapshot from(StackTraceElement frame, int causalDepth) {
        Objects.requireNonNull(frame, "frame");
        return new StackFrameSnapshot(
                safe(frame.getClassName()),
                safe(frame.getMethodName()),
                safe(frame.getFileName()),
                frame.getLineNumber(),
                frame.isNativeMethod(),
                safe(frame.getModuleName()),
                safe(frame.getModuleVersion()),
                safe(frame.getClassLoaderName()),
                causalDepth);
    }

    /**
     * Copies this frame with a different causal depth.
     *
     * @param depth new zero-based causal depth
     * @return copied frame with the requested depth
     */
    public StackFrameSnapshot withCausalDepth(int depth) {
        return new StackFrameSnapshot(
                className,
                methodName,
                fileName,
                lineNumber,
                nativeMethod,
                moduleName,
                moduleVersion,
                classLoaderName,
                depth);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
