package io.github.kalayciburak.stackmate.inference.jvm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.inference.InferenceAvailability;
import io.github.kalayciburak.stackmate.inference.InferenceFailure;
import io.github.kalayciburak.stackmate.inference.InferenceLanguage;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.inference.InferenceResult;
import io.github.kalayciburak.stackmate.inference.LocalInferenceEngine;
import io.github.kalayciburak.stackmate.inference.ModelDescriptor;
import io.github.kalayciburak.stackmate.inference.StructuredCause;
import io.github.kalayciburak.stackmate.inference.StructuredCheck;
import io.github.kalayciburak.stackmate.inference.StructuredInferenceOutput;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/** Jlama-backed local inference isolated in one managed child JVM. */
public final class JlamaLocalInferenceEngine implements LocalInferenceEngine {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private final Object lock = new Object();
    private final ModelDirectory modelDirectory;
    private final ObjectMapper mapper;
    private final ManagedChild child;
    private final int repairAttempts;
    private boolean closed;

    /** Creates a lazy engine for an explicit, already-provisioned local model directory. */
    public JlamaLocalInferenceEngine(Path modelDirectory) {
        this(modelDirectory, DEFAULT_TIMEOUT);
    }

    /** Creates a lazy engine with a bounded per-request timeout. */
    public JlamaLocalInferenceEngine(Path modelDirectory, Duration timeout) {
        this(modelDirectory, timeout, 1);
    }

    /** Creates a lazy engine with zero or one controlled structured-output repair attempt. */
    public JlamaLocalInferenceEngine(Path modelDirectory, Duration timeout, int repairAttempts) {
        this(
                modelDirectory,
                timeout,
                repairAttempts,
                configuredMapper(),
                ChildCommandFactory.currentRuntime(),
                ChildProcessLauncher.system());
    }

    JlamaLocalInferenceEngine(
            Path modelDirectory,
            Duration timeout,
            ObjectMapper mapper,
            ChildCommandFactory commandFactory,
            ChildProcessLauncher launcher) {
        this(modelDirectory, timeout, 1, mapper, commandFactory, launcher);
    }

    JlamaLocalInferenceEngine(
            Path modelDirectory,
            Duration timeout,
            int repairAttempts,
            ObjectMapper mapper,
            ChildCommandFactory commandFactory,
            ChildProcessLauncher launcher) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("timeout must be between zero and one hour");
        }
        if (repairAttempts < 0 || repairAttempts > 1) {
            throw new IllegalArgumentException("repairAttempts must be zero or one");
        }
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.repairAttempts = repairAttempts;
        this.modelDirectory = ModelDirectory.validate(modelDirectory, mapper);
        this.child =
                new ManagedChild(
                        this.modelDirectory.path(),
                        timeout,
                        mapper,
                        Objects.requireNonNull(commandFactory, "commandFactory"),
                        Objects.requireNonNull(launcher, "launcher"));
    }

    @Override
    public InferenceAvailability availability() {
        synchronized (lock) {
            if (closed) {
                return unavailable("local inference engine is closed");
            }
            try {
                child.command();
            } catch (ChildCommandFactory.UnsupportedPackagingException exception) {
                return unavailable(Protocol.boundedError(exception));
            }
            if (!child.mayStart()) {
                return unavailable("local inference child restart limit reached");
            }
            if (!child.lastFailure().isBlank()) {
                return new InferenceAvailability(
                        InferenceAvailability.Status.DEGRADED, child.lastFailure());
            }
            return InferenceAvailability.available();
        }
    }

    @Override
    public ModelDescriptor model() {
        return modelDirectory.descriptor();
    }

    @Override
    public InferenceResult infer(InferenceRequest request) throws InterruptedException {
        synchronized (lock) {
            if (closed) {
                return failed(
                        InferenceFailure.Kind.UNAVAILABLE,
                        "local inference engine is closed",
                        false);
            }
            if (request == null) {
                return failed(
                        InferenceFailure.Kind.REJECTED, "inference request is required", false);
            }
            if (!model().capabilities().supports(request.language())) {
                return failed(
                        InferenceFailure.Kind.REJECTED,
                        "requested language is not supported by the local model",
                        false);
            }
            if (!child.mayStart()) {
                return failed(
                        InferenceFailure.Kind.UNAVAILABLE,
                        "local inference child restart limit reached",
                        false);
            }

            long started = System.nanoTime();
            try {
                JsonNode raw = child.generate(mapper.valueToTree(request));
                if (!raw.isTextual()) {
                    return failed(
                            InferenceFailure.Kind.INVALID_OUTPUT,
                            "child returned a non-text model result",
                            false);
                }
                String json = raw.textValue();
                if (json.length() > request.maximumOutputCharacters()) {
                    return failed(
                            InferenceFailure.Kind.INVALID_OUTPUT,
                            "model output exceeds requested character limit",
                            false);
                }
                StructuredInferenceOutput output = parseWithRepair(request, json);
                return new InferenceResult.Success(
                        output, Duration.ofNanos(System.nanoTime() - started));
            } catch (TimeoutException exception) {
                return failed(InferenceFailure.Kind.TIMED_OUT, exception.getMessage(), true);
            } catch (ManagedChild.ChildFailureException exception) {
                return failed(
                        InferenceFailure.Kind.ENGINE_ERROR,
                        "local model rejected inference: " + Protocol.boundedError(exception),
                        true);
            } catch (JsonProcessingException exception) {
                return failed(
                        InferenceFailure.Kind.INVALID_OUTPUT,
                        "local model returned invalid structured JSON ("
                                + exception.getClass().getSimpleName()
                                + invalidCause(exception)
                                + ")",
                        false);
            } catch (IllegalArgumentException exception) {
                return failed(
                        InferenceFailure.Kind.INVALID_OUTPUT,
                        "local model returned a structurally invalid value",
                        false);
            } catch (ChildCommandFactory.UnsupportedPackagingException exception) {
                return failed(
                        InferenceFailure.Kind.UNAVAILABLE, Protocol.boundedError(exception), false);
            } catch (IOException exception) {
                return failed(
                        InferenceFailure.Kind.ENGINE_ERROR,
                        "local inference process failed: " + Protocol.boundedError(exception),
                        true);
            }
        }
    }

    private StructuredInferenceOutput parseWithRepair(InferenceRequest request, String generated)
            throws IOException, InterruptedException, TimeoutException {
        try {
            return parseOutput(normalizeJsonEnvelope(generated));
        } catch (JsonProcessingException | IllegalArgumentException invalid) {
            if (repairAttempts == 0) {
                throw invalid;
            }
            Protocol.RepairPayload repair = new Protocol.RepairPayload(request, generated);
            JsonNode rawRepair = child.repair(mapper.valueToTree(repair));
            if (!rawRepair.isTextual()) {
                throw new JsonProcessingException("child returned a non-text repair result") {};
            }
            String repaired = rawRepair.textValue();
            if (repaired.length() > request.maximumOutputCharacters()) {
                throw new JsonProcessingException(
                        "repaired output exceeds requested character limit") {};
            }
            return parseOutput(normalizeJsonEnvelope(repaired));
        }
    }

    private StructuredInferenceOutput parseOutput(String json) throws JsonProcessingException {
        ParsedOutput parsed = mapper.readValue(json, ParsedOutput.class);
        List<StructuredCause> causes =
                parsed.causes() == null
                        ? null
                        : parsed.causes().stream().map(ParsedCause::toDomain).toList();
        List<StructuredCheck> checks =
                parsed.checks() == null
                        ? null
                        : parsed.checks().stream().map(ParsedCheck::toDomain).toList();
        return new StructuredInferenceOutput(parsed.language(), parsed.summary(), causes, checks);
    }

    private static String normalizeJsonEnvelope(String generated) {
        String stripped = generated.strip();
        if (!stripped.startsWith("```") || !stripped.endsWith("```")) {
            return stripped;
        }
        int firstLineEnd = stripped.indexOf('\n');
        if (firstLineEnd < 0) {
            return stripped;
        }
        String opening = stripped.substring(0, firstLineEnd).strip();
        if (!opening.equals("```") && !opening.equalsIgnoreCase("```json")) {
            return stripped;
        }
        String content = stripped.substring(firstLineEnd + 1, stripped.length() - 3).strip();
        return content.startsWith("{") && content.endsWith("}") ? content : stripped;
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            child.close();
        }
    }

    private static InferenceResult failed(
            InferenceFailure.Kind kind, String message, boolean retryable) {
        return new InferenceResult.Failed(new InferenceFailure(kind, bounded(message), retryable));
    }

    private static InferenceAvailability unavailable(String detail) {
        return new InferenceAvailability(InferenceAvailability.Status.UNAVAILABLE, bounded(detail));
    }

    private static String bounded(String value) {
        String safe = value == null || value.isBlank() ? "local inference failed" : value;
        safe = safe.replaceAll("[\\p{Cc}&&[^\\t]]", " ").strip();
        return safe.substring(0, Math.min(safe.length(), 512));
    }

    private static String invalidCause(JsonProcessingException exception) {
        Throwable cause = exception.getCause();
        if (cause == null || cause.getMessage() == null || cause.getMessage().isBlank()) {
            return "";
        }
        String sanitized = DefaultSanitizer.defaults().sanitize(cause.getMessage()).strip();
        return sanitized.isBlank()
                ? ""
                : ": " + sanitized.substring(0, Math.min(sanitized.length(), 160));
    }

    private static ObjectMapper configuredMapper() {
        return new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    private record ParsedOutput(
            InferenceLanguage language,
            String summary,
            List<ParsedCause> causes,
            List<ParsedCheck> checks) {}

    private record ParsedCause(
            String causeId,
            String explanation,
            List<String> factIds,
            List<String> sourceLocationIds) {
        private StructuredCause toDomain() {
            return new StructuredCause(
                    causeId,
                    explanation,
                    factIds,
                    sourceLocationIds == null ? List.of() : sourceLocationIds);
        }
    }

    private record ParsedCheck(
            String checkId,
            String instruction,
            List<String> factIds,
            List<String> sourceLocationIds) {
        private StructuredCheck toDomain() {
            return new StructuredCheck(
                    checkId,
                    instruction,
                    factIds,
                    sourceLocationIds == null ? List.of() : sourceLocationIds);
        }
    }
}
