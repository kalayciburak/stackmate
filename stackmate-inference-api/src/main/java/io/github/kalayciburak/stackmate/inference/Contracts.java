package io.github.kalayciburak.stackmate.inference;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

final class Contracts {

    private static final Pattern STABLE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");

    private Contracts() {}

    static String requireStableId(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!STABLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a stable lowercase identifier");
        }
        return value;
    }

    static String requireBoundedText(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " exceeds limit " + maximumLength);
        }
        return value;
    }

    static List<String> immutableStableIds(List<String> values, String name, int maximumSize) {
        Objects.requireNonNull(values, name);
        List<String> copy = List.copyOf(values);
        if (copy.size() > maximumSize) {
            throw new IllegalArgumentException(name + " exceeds limit " + maximumSize);
        }
        copy.forEach(value -> requireStableId(value, name));
        if (copy.stream().distinct().count() != copy.size()) {
            throw new IllegalArgumentException(name + " must be unique");
        }
        return copy;
    }
}
