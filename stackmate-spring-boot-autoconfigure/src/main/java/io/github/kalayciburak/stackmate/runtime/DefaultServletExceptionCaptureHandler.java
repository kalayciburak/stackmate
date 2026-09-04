package io.github.kalayciburak.stackmate.runtime;

import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.capture.CapturedServletException;
import io.github.kalayciburak.stackmate.capture.ServletExceptionCaptureHandler;
import io.github.kalayciburak.stackmate.configuration.ProductionProfileGuard;
import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import io.github.kalayciburak.stackmate.deduplication.DeduplicationResult;
import io.github.kalayciburak.stackmate.deduplication.DeduplicationStore;
import io.github.kalayciburak.stackmate.export.IncidentExporter;
import io.github.kalayciburak.stackmate.fingerprint.ExceptionFingerprint;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.incident.JvmRuntimeContext;
import io.github.kalayciburak.stackmate.output.ConsoleRenderer;
import io.github.kalayciburak.stackmate.output.RenderOptions;
import io.github.kalayciburak.stackmate.output.RenderStyle;
import io.github.kalayciburak.stackmate.throwable.ApplicationFrameSelector;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/** Fail-open bridge from servlet capture to deterministic analysis and console rendering. */
public final class DefaultServletExceptionCaptureHandler implements ServletExceptionCaptureHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("STACKMATE");
    private static final Marker MARKER = MarkerFactory.getMarker("STACKMATE");

    private final ThrowableSnapshotter snapshotter;
    private final DeterministicDiagnosticEngine diagnosticEngine;
    private final DeduplicationStore deduplicationStore;
    private final ConsoleRenderer consoleRenderer;
    private final StackMateProperties properties;
    private final ProductionProfileGuard productionGuard;
    private final IncidentExporter incidentExporter;
    private final AtomicLong incidentSequence = new AtomicLong();

    /**
     * Creates a capture handler without filesystem incident export.
     *
     * @param snapshotter bounded throwable snapshotter
     * @param diagnosticEngine deterministic incident analyzer
     * @param deduplicationStore duplicate fingerprint store
     * @param consoleRenderer renderer for diagnostic log messages
     * @param properties StackMate runtime configuration
     * @param productionGuard precomputed production-profile policy
     */
    public DefaultServletExceptionCaptureHandler(
            ThrowableSnapshotter snapshotter,
            DeterministicDiagnosticEngine diagnosticEngine,
            DeduplicationStore deduplicationStore,
            ConsoleRenderer consoleRenderer,
            StackMateProperties properties,
            ProductionProfileGuard productionGuard) {
        this(
                snapshotter,
                diagnosticEngine,
                deduplicationStore,
                consoleRenderer,
                properties,
                productionGuard,
                null);
    }

    /**
     * Creates a capture handler with optional filesystem incident export.
     *
     * @param snapshotter bounded throwable snapshotter
     * @param diagnosticEngine deterministic incident analyzer
     * @param deduplicationStore duplicate fingerprint store
     * @param consoleRenderer renderer for diagnostic log messages
     * @param properties StackMate runtime configuration
     * @param productionGuard precomputed production-profile policy
     * @param incidentExporter exporter for first occurrences, or {@code null} to disable export
     */
    public DefaultServletExceptionCaptureHandler(
            ThrowableSnapshotter snapshotter,
            DeterministicDiagnosticEngine diagnosticEngine,
            DeduplicationStore deduplicationStore,
            ConsoleRenderer consoleRenderer,
            StackMateProperties properties,
            ProductionProfileGuard productionGuard,
            IncidentExporter incidentExporter) {
        this.snapshotter = Objects.requireNonNull(snapshotter, "snapshotter");
        this.diagnosticEngine = Objects.requireNonNull(diagnosticEngine, "diagnosticEngine");
        this.deduplicationStore = Objects.requireNonNull(deduplicationStore, "deduplicationStore");
        this.consoleRenderer = Objects.requireNonNull(consoleRenderer, "consoleRenderer");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.productionGuard = Objects.requireNonNull(productionGuard, "productionGuard");
        this.incidentExporter = incidentExporter;
        warnWhenProductionGuardBlocksAnalysis();
    }

    @Override
    public void capture(CapturedServletException capturedException) {
        Objects.requireNonNull(capturedException, "capturedException");
        if (!productionGuard.decision().analysisAllowed()) {
            return;
        }
        Throwable failure = capturedException.exception();
        if (ThrowableSnapshotter.isSevereJvmFailure(failure)) {
            renderSevereFailure(failure);
            return;
        }
        try {
            ThrowableSnapshot throwable = snapshotter.capture(failure);
            List<String> applicationPackages = applicationPackages(capturedException);
            ApplicationFrameSelector frameSelector =
                    new ApplicationFrameSelector(
                            applicationPackages,
                            properties.getDiagnostics().getMaximumApplicationFrames());
            IncidentSnapshot incident =
                    new IncidentSnapshot(
                            "CAP-" + Long.toUnsignedString(incidentSequence.incrementAndGet()),
                            Instant.now(),
                            throwable,
                            frameSelector.select(throwable),
                            capturedException.context().request(),
                            capturedException.context().springRuntime(),
                            JvmRuntimeContext.current());
            DeterministicResult result = diagnosticEngine.analyze(incident);
            DeduplicationResult duplicate = recordOccurrence(result.fingerprint());
            render(result, duplicate);
            export(result, duplicate);
        } catch (Throwable internalFailure) {
            debugInternalFailure(internalFailure);
        }
    }

    private void export(DeterministicResult result, DeduplicationResult duplicate) {
        if (incidentExporter == null || duplicate.duplicate()) {
            return;
        }
        try {
            incidentExporter.export(result);
        } catch (Throwable exportFailure) {
            if (properties.getConsole().isDebugLogging()) {
                LOGGER.debug(
                        MARKER,
                        "StackMate incident export failed safely: {}",
                        exportFailure.getClass().getName());
            }
        }
    }

    private List<String> applicationPackages(CapturedServletException capturedException) {
        List<String> packages = new ArrayList<>(properties.getApplicationPackages());
        addIfPresent(packages, capturedException.context().springRuntime().applicationPackage());
        if (packages.isEmpty()) {
            packages.add("stackmate.application.not.detected");
        }
        return packages.stream().distinct().toList();
    }

    private void addIfPresent(List<String> packages, String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            packages.add(candidate);
        }
    }

    private DeduplicationResult recordOccurrence(ExceptionFingerprint fingerprint) {
        if (properties.getDeduplication().isEnabled()) {
            return deduplicationStore.record(fingerprint.value(), false);
        }
        Instant now = Instant.now();
        return new DeduplicationResult(1, false, false, now, now);
    }

    private void render(DeterministicResult result, DeduplicationResult duplicate) {
        if (!properties.getConsole().isEnabled()) {
            return;
        }
        RenderOptions options = renderOptions();
        String message =
                duplicate.duplicate()
                        ? consoleRenderer.renderDuplicate(result, duplicate, options)
                        : consoleRenderer.render(result, options);
        LOGGER.error(MARKER, "{}", System.lineSeparator() + message);
    }

    private RenderOptions renderOptions() {
        RenderStyle style =
                properties.getConsole().getStyle().name().equals("COMPACT")
                        ? RenderStyle.COMPACT
                        : RenderStyle.BOX;
        return new RenderOptions(
                style,
                properties.getConsole().getMaximumWidth(),
                properties.getConsole().isShowApplicationTrace(),
                properties.getConsole().isShowTechnicalDetails(),
                properties.getConsole().isShowCauseChain());
    }

    private void renderSevereFailure(Throwable failure) {
        if (!properties.getConsole().isEnabled()) {
            return;
        }
        LOGGER.error(
                MARKER,
                "[StackMate] Severe JVM failure observed: {}.",
                failure.getClass().getName());
    }

    private void warnWhenProductionGuardBlocksAnalysis() {
        ProductionProfileGuard.Decision decision = productionGuard.decision();
        if (decision.warningRequired()) {
            LOGGER.warn(
                    MARKER,
                    "StackMate analysis is disabled for production profile(s): {}. "
                            + "Profile detection is a safety aid, not a security boundary.",
                    decision.matchedProductionProfiles());
        }
    }

    private void debugInternalFailure(Throwable failure) {
        if (properties.getConsole().isDebugLogging()) {
            LOGGER.debug(
                    MARKER,
                    "StackMate deterministic analysis failed safely: {}",
                    failure.getClass().getName());
        }
    }
}
