package io.github.kalayciburak.stackmate.configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class PropertyValidation {

    private PropertyValidation() {}

    static int positive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    static int range(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    static double nonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    static double range(double value, double minimum, double maximum, String name) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be finite and between " + minimum + " and " + maximum);
        }
        return value;
    }

    static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    static String text(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }

    static List<String> textList(List<String> values, String name) {
        return textList(values, name, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    static List<String> textList(
            List<String> values, String name, int maximumEntries, int maximumLength) {
        Objects.requireNonNull(values, name);
        if (values.size() > maximumEntries) {
            throw new IllegalArgumentException(
                    name + " must contain at most " + maximumEntries + " entries");
        }
        List<String> result = new ArrayList<>(values.size());
        for (String value : values) {
            String validated = text(value, name);
            if (validated.codePointCount(0, validated.length()) > maximumLength) {
                throw new IllegalArgumentException(
                        name + " entries must contain at most " + maximumLength + " characters");
            }
            result.add(validated);
        }
        return List.copyOf(result);
    }
}
