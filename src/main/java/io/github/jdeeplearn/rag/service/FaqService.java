package io.github.jdeeplearn.rag.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.jdeeplearn.rag.repository.FaqRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates:
 *  1. Call embedding microservice to get question embedding.
 *  2. Call Couchbase FTS vector index via FaqRepository.
 *  3. Apply similarity threshold and return best answer or fallback.
 *
 * IMPORTANT:
 *  - repo.score is a SIMILARITY / RELEVANCE score from FTS (higher = better).
 *  - matchThreshold is a MINIMUM similarity; match is accepted if score >= threshold.
 */
@Service
public class FaqService {

    private static final Logger log = LoggerFactory.getLogger(FaqService.class);

    private final FaqRepository repository;
    private final RestClient restClient;
    private final String embeddingUrl;
    /**
     * Minimum similarity score required to accept a match.
     * For FTS vector search, typical values are ~0.7–1.0 for very close matches.
     */
    private final double matchThreshold;
    private final String fallbackNumber;

    public FaqService(
            FaqRepository repository,
            @Value("${app.embeddingServiceUrl}") String embeddingUrl,
            @Value("${app.matchThreshold:0.7}") double matchThreshold,   // now acts as MIN similarity
            @Value("${app.fallbackNumber:1-800-123-4567}") String fallbackNumber
    ) {
        this.repository = repository;
        this.embeddingUrl = embeddingUrl;
        this.matchThreshold = matchThreshold;
        this.fallbackNumber = fallbackNumber;

        SimpleClientHttpRequestFactory reqFactory = new SimpleClientHttpRequestFactory();
        reqFactory.setConnectTimeout(2000);
        reqFactory.setReadTimeout(4000);

        this.restClient = RestClient.builder()
                .requestFactory(reqFactory)
                .build();
    }

    /** Main entry point used by the controller. */
    public FaqResponse answer(String question) {
        Instant ts = Instant.now();
        try {
            List<Double> vector = getEmbedding(question);
            if (CollectionUtils.isEmpty(vector)) {
                log.warn("Embedding service returned empty vector for question='{}'", question);
                return fallback();
            }

            var matchOpt = repository.findBestMatch(vector);

            if (matchOpt.isPresent()) {
                var m = matchOpt.get();
                double score = m.score(); // similarity from FTS (higher = better)

                boolean accepted = score >= matchThreshold;
                log.info("Q='{}' | similarityScore={} | threshold={} | accepted={} | ts={}",
                        question, score, matchThreshold, accepted, ts);

                if (accepted) {
                    return new FaqResponse(m.answer(), m.image(), m.link());
                }
            } else {
                log.info("Q='{}' | no match returned from repository | ts={}", question, ts);
            }

            // No match or below threshold
            return fallback();

        } catch (Exception e) {
            log.error("Error processing question='{}'", question, e);
            return fallback();
        }
    }

    /** Calls the Python embedding microservice. Expects {"provider","model","embedding":[...]} */
    private List<Double> getEmbedding(String text) {
        Map<String, Object> req = Map.of("text", text);

        var resp = restClient.post()
                .uri(embeddingUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .toEntity(EmbeddingResponse.class);

        EmbeddingResponse body = resp.getBody();
        return body == null || body.embedding() == null ? List.of() : body.embedding();
    }

    /** Fallback response when no FAQ match or on error. */
    private FaqResponse fallback() {
        return new FaqResponse(
                "I can’t answer this question. Please reach out to customer care at " + fallbackNumber + ".",
                null,
                null
        );
    }

    /** DTO for embedding microservice response. */
    private record EmbeddingResponse(String provider, String model, List<Double> embedding) {}

    /** Outbound DTO returned to clients. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FaqResponse(String answer, String image, String link) {}
}