package io.github.kalayciburak.stackmate.capture;

/** Receives escaping servlet exceptions without taking ownership of HTTP error handling. */
@FunctionalInterface
public interface ServletExceptionCaptureHandler {

    void capture(CapturedServletException capturedException);
}
