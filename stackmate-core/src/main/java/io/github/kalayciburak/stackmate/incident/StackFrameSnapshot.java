package io.github.kalayciburak.stackmate.incident;

import java.util.Objects;

/** An immutable, serialization-safe copy of one stack frame. */
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
