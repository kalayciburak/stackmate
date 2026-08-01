package io.github.kalayciburak.stackmate.output;

import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.deduplication.DeduplicationResult;
import io.github.kalayciburak.stackmate.fingerprint.ExceptionFingerprint;

/**
 * Extension point for renderer-neutral console messages.
 *
 * <p>The displayed incident ID is stable across equivalent failures: {@code STM-} followed by the
 * first eight uppercase hexadecimal characters of the exception fingerprint. It deliberately does
 * not expose the capture-specific {@code IncidentSnapshot.incidentId()}.
 */
public interface ConsoleRenderer {

    String render(DeterministicResult result, RenderOptions options);

    String renderDuplicate(
            DeterministicResult result, DeduplicationResult duplicate, RenderOptions options);

    /** Minimal, allocation-conscious message for severe JVM failures. */
    String renderSevereJvmFailure(
            ExceptionFingerprint fingerprint, String failureType, RenderOptions options);
}
