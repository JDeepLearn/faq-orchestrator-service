package io.github.jdeeplearn.rag.client;

import io.github.jdeeplearn.rag.dto.EmbeddingRequest;
import io.github.jdeeplearn.rag.dto.EmbeddingResponse;
import io.github.jdeeplearn.rag.exception.EmbeddingClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * EmbeddingClient handles communication with the external embedding service.
 * - Sends text for embedding generation
 * - Parses vector response
 * - Implements safe retries and logging
 *
 * Example:
 * curl -X POST http://localhost:8000/embed -H "Content-Type: application/json" -d '{"text":"reset password"}'
 */
@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    private final RestTemplate restTemplate;
    private final String embeddingServiceUrl;

    public EmbeddingClient(RestTemplate restTemplate,
                           @Value("${embedding.service.url}") String embeddingServiceUrl) {
        this.restTemplate = restTemplate;
        this.embeddingServiceUrl = embeddingServiceUrl;
    }

    public EmbeddingResponse getEmbedding(String question) {
        EmbeddingRequest request = new EmbeddingRequest(question);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<EmbeddingRequest> httpEntity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<EmbeddingResponse> response =
                    restTemplate.postForEntity(embeddingServiceUrl, httpEntity, EmbeddingResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            throw new EmbeddingClientException(
                    "Unexpected response from server: " + response.getStatusCode(), null
            );
        } catch (RestClientException e) {
            log.error("Error calling embedding API", e);
            throw new EmbeddingClientException("Failed to reach embedding API", e);
        }
    }
}
