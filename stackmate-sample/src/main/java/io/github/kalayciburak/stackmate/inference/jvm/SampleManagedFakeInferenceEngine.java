package io.github.kalayciburak.stackmate.inference.jvm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.inference.InferenceAvailability;
import io.github.kalayciburak.stackmate.inference.InferenceFailure;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.inference.InferenceResult;
import io.github.kalayciburak.stackmate.inference.LocalInferenceEngine;
import io.github.kalayciburak.stackmate.inference.ModelCapabilities;
import io.github.kalayciburak.stackmate.inference.ModelDescriptor;
import io.github.kalayciburak.stackmate.inference.StructuredInferenceOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/** Sample-only fake that exercises the real managed process protocol without model weights. */
public final class SampleManagedFakeInferenceEngine implements LocalInferenceEngine {
    private static final String CHILD_ENTRY =
            "io/github/kalayciburak/stackmate/inference/jvm/SampleFakeInferenceChildMain.class";
    private static final ModelDescriptor MODEL =
            new ModelDescriptor(
                    "stackmate-e2e-fake",
                    "StackMate packaged E2E fake child",
                    "1",
                    new ModelCapabilities(true, true, true, 16_000, 8_192));

    private final ObjectMapper mapper = Protocol.strictMapper();
    private final ManagedChild child;
    private boolean closed;

    public SampleManagedFakeInferenceEngine(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        ChildCommandFactory commandFactory =
                new ChildCommandFactory(
                        Path.of(System.getProperty("java.home")),
                        System.getProperty("os.name", ""),
                        System.getProperty("java.class.path", ""),
                        SampleFakeInferenceChildMain.class.getName(),
                        CHILD_ENTRY);
        this.child =
                new ManagedChild(
                        Path.of(System.getProperty("java.io.tmpdir")),
                        timeout,
                        mapper,
                        commandFactory,
                        ChildProcessLauncher.system());
    }

    @Override
    public synchronized InferenceAvailability availability() {
        if (closed || !child.mayStart()) {
            return new InferenceAvailability(
                    InferenceAvailability.Status.UNAVAILABLE,
                    "packaged E2E fake child is unavailable");
        }
        try {
            child.command();
            return InferenceAvailability.available();
        } catch (RuntimeException failure) {
            return new InferenceAvailability(
                    InferenceAvailability.Status.UNAVAILABLE, Protocol.boundedError(failure));
        }
    }

    @Override
    public ModelDescriptor model() {
        return MODEL;
    }

    @Override
    public synchronized InferenceResult infer(InferenceRequest request)
            throws InterruptedException {
        if (closed || request == null) {
            return failed(InferenceFailure.Kind.UNAVAILABLE, "fake child is unavailable", false);
        }
        long started = System.nanoTime();
        try {
            JsonNode response = child.generate(mapper.valueToTree(request));
            StructuredInferenceOutput output =
                    mapper.treeToValue(response, StructuredInferenceOutput.class);
            return new InferenceResult.Success(
                    output, Duration.ofNanos(System.nanoTime() - started));
        } catch (TimeoutException failure) {
            return failed(InferenceFailure.Kind.TIMED_OUT, "fake child timed out", true);
        } catch (ManagedChild.ChildFailureException failure) {
            return failed(InferenceFailure.Kind.ENGINE_ERROR, Protocol.boundedError(failure), true);
        } catch (IOException | RuntimeException failure) {
            return failed(InferenceFailure.Kind.ENGINE_ERROR, Protocol.boundedError(failure), true);
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            child.close();
        }
    }

    private static InferenceResult failed(
            InferenceFailure.Kind kind, String message, boolean retryable) {
        String bounded =
                message == null || message.isBlank()
                        ? "packaged E2E fake inference failed"
                        : message.substring(0, Math.min(message.length(), 512));
        return new InferenceResult.Failed(new InferenceFailure(kind, bounded, retryable));
    }
}
