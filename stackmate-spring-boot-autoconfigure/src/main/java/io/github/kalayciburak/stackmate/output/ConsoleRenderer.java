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

    /**
     * Renders the full deterministic diagnosis for a first occurrence.
     *
     * @param result analyzed incident and its fingerprint
     * @param options per-call presentation options
     * @return rendered plain-text diagnosis
     */
    String render(DeterministicResult result, RenderOptions options);

    /**
     * Renders a concise message for a repeated fingerprint.
     *
     * @param result analyzed incident and its fingerprint
     * @param duplicate deduplication state including the occurrence count
     * @param options per-call presentation options
     * @return rendered plain-text duplicate message
     */
    String renderDuplicate(
            DeterministicResult result, DeduplicationResult duplicate, RenderOptions options);

    /**
     * Renders a minimal message for a severe JVM failure.
     *
     * @param fingerprint stable fingerprint of the failure
     * @param failureType throwable type name
     * @param options per-call presentation options
     * @return rendered plain-text severe-failure message
     */
    String renderSevereJvmFailure(
            ExceptionFingerprint fingerprint, String failureType, RenderOptions options);
}
