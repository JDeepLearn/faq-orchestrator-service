package io.github.jdeeplearn.rag.dto;

import java.util.List;

/**
 * Represents a response from the embedding service.
 *
 * Example JSON:
 * {
 *   "provider": "hf-local",
 *   "model": "intfloat/e5-large-v2",
 *   "dim": 1024,
 *   "embedding": [0.015610592, -0.0346224, ...]
 * }
 */
public record EmbeddingResponse(
        String provider,
        String model,
        int dim,
        List<Double> embedding
) {}
