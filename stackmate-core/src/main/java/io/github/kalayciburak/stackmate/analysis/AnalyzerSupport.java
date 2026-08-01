package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.throwable.ThrowableNode;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class AnalyzerSupport {

    private AnalyzerSupport() {}

    static List<NodeFact> nodes(ThrowableSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<NodeFact> facts = new ArrayList<>();
        for (int index = 0; index < snapshot.causeChain().size(); index++) {
            ThrowableNode node = snapshot.causeChain().get(index);
            String path = "throwable.causeChain[" + index + "]";
            facts.add(new NodeFact(facts.size(), path, node));
            appendSuppressed(node, path, facts);
        }
        return List.copyOf(facts);
    }

    static boolean hasType(ThrowableNode node, String fullyQualifiedName) {
        return fullyQualifiedName.equals(node.type());
    }

    static boolean hasSimpleType(ThrowableNode node, String simpleName) {
        String type = node.type();
        int separator = Math.max(type.lastIndexOf('.'), type.lastIndexOf('$'));
        return type.substring(separator + 1).equals(simpleName);
    }

    static boolean messageContains(ThrowableNode node, String fragment) {
        String message = node.message();
        return message != null
                && message.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private static void appendSuppressed(ThrowableNode node, String path, List<NodeFact> facts) {
        for (int index = 0; index < node.suppressed().size(); index++) {
            ThrowableNode suppressed = node.suppressed().get(index);
            String suppressedPath = path + ".suppressed[" + index + "]";
            facts.add(new NodeFact(facts.size(), suppressedPath, suppressed));
            appendSuppressed(suppressed, suppressedPath, facts);
        }
    }

    record NodeFact(int ordinal, String path, ThrowableNode node) {}
}
