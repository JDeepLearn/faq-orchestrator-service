package io.github.jdeeplearn.rag.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

/**
 * Maintains a rolling window of observed FTS TEXT_SCORE() values
 * to auto-calibrate lexical normalization for hybrid scoring.
 *
 * This prevents the need to hardcode a divisor such as /5.0.
 * It adapts automatically as Couchbase FTS scoring drifts.
 */
public class ScoreNormalizer {

    private static final Logger log = LoggerFactory.getLogger(ScoreNormalizer.class);
    private static final int WINDOW_SIZE = 100;

    private final LinkedList<Double> history = new LinkedList<>();

    public synchronized void record(double textScore) {
        if (Double.isNaN(textScore) || textScore <= 0) return;
        history.add(textScore);
        if (history.size() > WINDOW_SIZE) {
            history.removeFirst();
        }
    }

    /**
     * Returns the current divisor to scale TEXT_SCORE() to roughly [0,1].
     * The divisor is the rolling mean of past FTS scores, clamped 2–10.
     */
    public synchronized double currentDivisor() {
        if (history.isEmpty()) return 5.0; // default
        double avg = history.stream().mapToDouble(Double::doubleValue).average().orElse(5.0);
        double divisor = Math.max(2.0, Math.min(10.0, avg));
        log.debug("ScoreNormalizer divisor={}", divisor);
        return divisor;
    }
}
