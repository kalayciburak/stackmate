package io.github.kalayciburak.stackmate.inference;

/** A bounded source location that model output may reference by stable ID. */
public record SourceLocation(
        String id, String className, String methodName, String fileName, int lineNumber) {

    public SourceLocation {
        id = Contracts.requireStableId(id, "id");
        className = Contracts.requireBoundedText(className, "className", 256);
        methodName = Contracts.requireBoundedText(methodName, "methodName", 128);
        fileName = Contracts.requireBoundedText(fileName, "fileName", 256);
        if (lineNumber < -1) {
            throw new IllegalArgumentException("lineNumber must be -1 or greater");
        }
    }
}
