package io.github.kalayciburak.stackmate.throwable;

import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Selects bounded application-owned frames, ranked nearest to the root cause. */
public final class ApplicationFrameSelector {

    private static final List<String> FRAMEWORK_PREFIXES =
            List.of(
                    "java.",
                    "javax.",
                    "jakarta.",
                    "jdk.",
                    "sun.",
                    "com.sun.",
                    "org.springframework.",
                    "org.apache.catalina.",
                    "org.apache.tomcat.",
                    "com.zaxxer.hikari.",
                    "org.hibernate.",
                    "com.fasterxml.jackson.",
                    "org.eclipse.jetty.",
                    "io.undertow.",
                    "io.netty.",
                    "reactor.core.");

    private static final List<String> GENERATED_MARKERS =
            List.of(
                    "$$SpringCGLIB$$",
                    "$$EnhancerBySpringCGLIB$$",
                    "$$ByteBuddy$$",
                    "$$Lambda$",
                    "$HibernateProxy$",
                    "$Proxy",
                    "jdk.proxy");

    private final List<String> applicationPackages;
    private final int maxFrames;

    /**
     * Creates a selector for explicitly configured application packages.
     *
     * @param applicationPackages package prefixes considered application-owned
     * @param maxFrames maximum number of frames returned
     */
    public ApplicationFrameSelector(List<String> applicationPackages, int maxFrames) {
        Objects.requireNonNull(applicationPackages, "applicationPackages");
        if (applicationPackages.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one explicit application package is required");
        }
        List<String> packages = new ArrayList<>(applicationPackages.size());
        for (String packageName : applicationPackages) {
            Objects.requireNonNull(packageName, "application package");
            String normalized =
                    packageName.endsWith(".")
                            ? packageName.substring(0, packageName.length() - 1)
                            : packageName;
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("Application packages must not be blank");
            }
            packages.add(normalized);
        }
        if (maxFrames < 1) {
            throw new IllegalArgumentException("maxFrames must be positive");
        }
        this.applicationPackages = List.copyOf(packages);
        this.maxFrames = maxFrames;
    }

    /**
     * Selects and ranks unique application-owned frames from a throwable snapshot.
     *
     * @param snapshot captured throwable structure to inspect
     * @return an immutable, bounded list ordered by diagnostic relevance
     */
    public List<StackFrameSnapshot> select(ThrowableSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<RankedFrame> candidates = new ArrayList<>();
        int[] encounterOrder = {0};
        for (ThrowableNode node : snapshot.causeChain()) {
            collect(node, false, candidates, encounterOrder);
        }
        candidates.sort(
                Comparator.comparingInt(RankedFrame::provenanceRank)
                        .thenComparing(Comparator.comparingInt(RankedFrame::causalDepth).reversed())
                        .thenComparingInt(RankedFrame::frameIndex)
                        .thenComparingInt(RankedFrame::encounterOrder));

        List<StackFrameSnapshot> selected = new ArrayList<>(maxFrames);
        Set<FrameKey> seen = new HashSet<>();
        for (RankedFrame candidate : candidates) {
            FrameKey key = FrameKey.from(candidate.frame());
            if (seen.add(key)) {
                selected.add(candidate.frame());
                if (selected.size() == maxFrames) {
                    break;
                }
            }
        }
        return List.copyOf(selected);
    }

    private void collect(
            ThrowableNode node,
            boolean suppressedProvenance,
            List<RankedFrame> candidates,
            int[] encounterOrder) {
        for (int index = 0; index < node.frames().size(); index++) {
            StackFrameSnapshot frame = node.frames().get(index);
            if (isApplicationFrame(frame.className()) && !isGeneratedNoise(frame.className())) {
                candidates.add(
                        new RankedFrame(
                                frame,
                                suppressedProvenance ? 1 : 0,
                                node.depth(),
                                index,
                                encounterOrder[0]++));
            }
        }
        for (ThrowableNode suppressed : node.suppressed()) {
            collect(suppressed, true, candidates, encounterOrder);
        }
    }

    private boolean isApplicationFrame(String className) {
        String owningPackage =
                applicationPackages.stream()
                        .filter(
                                packageName ->
                                        className.equals(packageName)
                                                || className.startsWith(packageName + '.'))
                        .findFirst()
                        .orElse("");
        if (owningPackage.isEmpty()) {
            return false;
        }
        return FRAMEWORK_PREFIXES.stream().noneMatch(className::startsWith)
                || FRAMEWORK_PREFIXES.stream().anyMatch(owningPackage::startsWith);
    }

    private boolean isGeneratedNoise(String className) {
        return GENERATED_MARKERS.stream().anyMatch(className::contains);
    }

    private record RankedFrame(
            StackFrameSnapshot frame,
            int provenanceRank,
            int causalDepth,
            int frameIndex,
            int encounterOrder) {}

    private record FrameKey(String className, String methodName, String fileName, int lineNumber) {
        private static FrameKey from(StackFrameSnapshot frame) {
            return new FrameKey(
                    frame.className(), frame.methodName(), frame.fileName(), frame.lineNumber());
        }
    }
}
