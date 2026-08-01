package io.github.kalayciburak.stackmate.benchmark.corpus.qualification;

import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.throwable.SqlExceptionDetails;
import io.github.kalayciburak.stackmate.throwable.ThrowableNode;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Builds immutable incidents from the frozen blind recipes. */
public final class QualificationBlindIncidentFactory {

    private static final Instant CAPTURE_TIME = Instant.parse("2026-08-02T00:00:00Z");
    private final QualificationBlindRuntimeRecipeTest runtimeRecipes =
            new QualificationBlindRuntimeRecipeTest();

    public IncidentSnapshot create(QualificationBlindCase benchmarkCase) throws Exception {
        StackFrameSnapshot frame = applicationFrame(benchmarkCase);
        ThrowableSnapshot throwable;
        if (benchmarkCase.realExecution()) {
            Throwable failure = runtimeRecipes.execute(benchmarkCase.recipe());
            Throwable root = deepest(failure);
            root.setStackTrace(
                    new StackTraceElement[] {
                        new StackTraceElement(
                                frame.className(),
                                frame.methodName(),
                                frame.fileName(),
                                frame.lineNumber())
                    });
            throwable = ThrowableSnapshotter.defaults().capture(failure);
        } else {
            throwable = ambiguousThrowable(benchmarkCase, frame);
        }
        return IncidentSnapshot.of(
                "B5-" + benchmarkCase.caseId(), CAPTURE_TIME, throwable, List.of(frame));
    }

    private ThrowableSnapshot ambiguousThrowable(
            QualificationBlindCase benchmarkCase, StackFrameSnapshot frame) {
        if (!benchmarkCase.recipe().equals("blind.unknown.ambiguous-wrapper")
                && !benchmarkCase.recipe().equals("blind.unknown.capture-metadata")) {
            throw new IllegalArgumentException(
                    "unsupported synthetic blind recipe: " + benchmarkCase.recipe());
        }
        if (benchmarkCase.recipe().equals("blind.unknown.capture-metadata")) {
            ThrowableNode root =
                    new ThrowableNode(
                            benchmarkCase.expectedRootType(),
                            "throwable metadata capture failed",
                            List.of(frame),
                            List.of(),
                            SqlExceptionDetails.none(),
                            0,
                            false,
                            false,
                            false,
                            false,
                            false,
                            List.of("throwable.type.unavailable"));
            return new ThrowableSnapshot(List.of(root), 1, false);
        }
        ThrowableNode wrapper =
                new ThrowableNode(
                        "java.util.concurrent.CompletionException",
                        "asynchronous operation failed",
                        List.of(),
                        List.of(),
                        SqlExceptionDetails.none(),
                        0,
                        true,
                        false,
                        false,
                        false,
                        false,
                        List.of());
        ThrowableNode root =
                new ThrowableNode(
                        benchmarkCase.expectedRootType(),
                        DefaultSanitizer.defaults()
                                .sanitize(
                                        "exceptionMessage",
                                        "operation stopped with no classified evidence"),
                        List.of(frame.withCausalDepth(1)),
                        List.of(),
                        SqlExceptionDetails.none(),
                        1,
                        false,
                        false,
                        false,
                        false,
                        false,
                        List.of());
        return new ThrowableSnapshot(List.of(wrapper, root), 2, false);
    }

    private StackFrameSnapshot applicationFrame(QualificationBlindCase benchmarkCase) {
        String domain =
                benchmarkCase.domain().name().toLowerCase(Locale.ROOT).replace('_', '.');
        return StackFrameSnapshot.from(
                new StackTraceElement(
                        "com.example.blind." + domain + ".BlindScenarioService",
                        "execute",
                        "BlindScenarioService.java",
                        120 + Math.floorMod(benchmarkCase.caseId().hashCode(), 70)),
                0);
    }

    private Throwable deepest(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current.getCause() != null && visited.add(current)) {
            current = current.getCause();
        }
        return current;
    }
}
