package io.github.kalayciburak.stackmate.benchmark.evaluation;

/**
 * A transparent numerator/denominator metric; no result is inferred when the denominator is zero.
 */
public record MetricScore(int matched, int total, double rate) {

    public MetricScore(int matched, int total) {
        this(matched, total, total == 0 ? 0.0 : (double) matched / total);
    }

    public MetricScore {
        if (matched < 0 || total < 0 || matched > total || !Double.isFinite(rate)) {
            throw new IllegalArgumentException("invalid metric score");
        }
    }
}
