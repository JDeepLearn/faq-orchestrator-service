package io.github.jdeeplearn.rag.service;

import io.github.jdeeplearn.rag.client.EmbeddingClient;
import io.github.jdeeplearn.rag.config.FaqDocument;
import io.github.jdeeplearn.rag.dto.EmbeddingResponse;
import io.github.jdeeplearn.rag.dto.FaqResponse;
import io.github.jdeeplearn.rag.repository.FaqRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Service orchestrating embedding + Couchbase hybrid vector search.
 * Uses FaqRepository.FaqMatch instead of MatchResult (post-FTS refactor).
 */
@Service
public class FaqService {

    private static final Logger log = LoggerFactory.getLogger(FaqService.class);

    private final FaqRepository repository;
    private final MessageSource messages;
    private final EmbeddingClient embeddingClient;
    private final double matchThreshold;
    private final double softThreshold;
    private final String fallbackNumber;

    public FaqService(
            FaqRepository repository,
            MessageSource messages, EmbeddingClient embeddingClient,
            @Value("${faq.match-threshold:0.9}") double matchThreshold,
            @Value("${faq.soft-match-threshold:0.75}") double softThreshold,
            @Value("${faq.fallback.number:1-800-123-4567}") String fallbackNumber
    ) {
        this.repository = repository;
        this.messages = messages;
        this.embeddingClient = embeddingClient;
        this.matchThreshold = matchThreshold;
        this.softThreshold = softThreshold;
        this.fallbackNumber = fallbackNumber;
    }

    /**
     * Main entrypoint for the /api/ask controller.
     */
    public FaqResponse handleQuestion(String question, Locale locale) {
        if (question == null || question.isBlank()) {
            return fallback("faq.fallback.empty", locale);
        }

        EmbeddingResponse embeddingResponse;
        try {
            embeddingResponse = embeddingClient.getEmbedding(question);
        } catch (Exception e) {
            log.error("Embedding service error for '{}': {}", question, e.getMessage());
            return fallback("faq.fallback.technical", locale);
        }

        if (embeddingResponse == null || embeddingResponse.embedding().isEmpty()) {
            log.warn("Empty embedding returned for question='{}'", question);
            return fallback("faq.fallback.technical", locale);
        }
        var embedding = embeddingResponse.embedding();
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = embedding.get(i).floatValue();
        }

        List<FaqRepository.FaqMatch> matches = repository.findTopKHybrid(vector, question);

        if (matches.isEmpty()) {
            return fallback("faq.fallback.no-match", locale);
        }

        FaqRepository.FaqMatch best = matches.getFirst();
        double score = best.score();

        boolean confident = score >= matchThreshold;
        boolean lowConfidence = score < matchThreshold && score >= softThreshold;

        log.info("Q='{}' | score={} | found={} | ts={}", question, score, confident, Instant.now());

        if (confident) {
            return repository.getFaqDocumentById(best.id());
        } else if (lowConfidence) {
            return fallback("faq.fallback.low-confidence", locale);
        } else {
            return fallback("faq.fallback.no-match", locale);
        }
    }


    /**
     * Builds a localized fallback message using message properties.
     */
    private FaqResponse fallback(String code, Locale locale) {
        String msg = messages.getMessage(code, new Object[]{fallbackNumber}, locale);
        return new FaqResponse(msg, null, null);
    }
}
