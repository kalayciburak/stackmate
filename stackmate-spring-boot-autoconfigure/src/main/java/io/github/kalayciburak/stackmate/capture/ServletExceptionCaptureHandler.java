package io.github.kalayciburak.stackmate.capture;

/** Receives escaping servlet exceptions without taking ownership of HTTP error handling. */
@FunctionalInterface
public interface ServletExceptionCaptureHandler {

    /**
     * Observes an exception and its bounded context without handling the HTTP failure.
     *
     * @param capturedException escaping exception and captured servlet context
     */
    void capture(CapturedServletException capturedException);
}
