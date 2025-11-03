package io.github.jdeeplearn.rag.repository;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Repository for hybrid vector search:
 * - Vector similarity via FTS REST API (port 8094)
 * - Full document retrieval via KV get (port 11210)
 */
@Repository
public class FaqRepository {

    private static final Logger log = LoggerFactory.getLogger(FaqRepository.class);

    private final RestClient restClient;
    private final String ftsUrl;
    private final String username;
    private final String password;
    private final Cluster cluster;
    private final Collection collection;

    public FaqRepository(
            @Value("${couchbase.connectionString}") String connectionString,
            @Value("${couchbase.username}") String username,
            @Value("${couchbase.password}") String password,
            @Value("${couchbase.bucket}") String bucketName,
            @Value("${couchbase.scope}") String scopeName,
            @Value("${couchbase.collection:faqs}") String collectionName,
            @Value("${couchbase.searchPort:8094}") int searchPort) {

        // Build cluster connection
        this.cluster = Cluster.connect(connectionString, username, password);
        this.collection = cluster.bucket(bucketName)
                .scope(scopeName)
                .collection(collectionName);

        // Normalize host for FTS REST API
        String hostPart = connectionString
                .replace("couchbase://", "")
                .replace("couchbases://", "");
        String host = hostPart.split(",")[0].trim();
        this.ftsUrl = "http://" + host + ":" + searchPort + "/api/index/faq_vectors/query";

        this.username = username;
        this.password = password;

        SimpleClientHttpRequestFactory reqFactory = new SimpleClientHttpRequestFactory();
        reqFactory.setConnectTimeout(3000);
        reqFactory.setReadTimeout(6000);
        this.restClient = RestClient.builder()
                .requestFactory(reqFactory)
                .build();
    }

    /**
     * Executes vector search via FTS REST API, then fetches the top document from KV.
     */
    public Optional<MatchResult> findBestMatch(List<Double> vector) {
        try {
            // FTS expects an array of knn objects
            Map<String, Object> knnObject = Map.of(
                    "field", "question_vector",
                    "vector", vector,
                    "k", 1
            );

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("knn", List.of(knnObject));
            body.put("size", 1);

            String encodedAuth = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

            var responseEntity = restClient.post()
                    .uri(ftsUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> h.set("Authorization", "Basic " + encodedAuth))
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class);

            Map<?, ?> respBody = responseEntity.getBody();
            if (respBody == null) {
                log.warn("FTS response body is null");
                return Optional.empty();
            }

            Object hitsObj = respBody.get("hits");
            if (!(hitsObj instanceof List<?> hits) || hits.isEmpty()) {
                log.info("FTS search returned no hits");
                return Optional.empty();
            }

            Map<?, ?> firstHit = (Map<?, ?>) hits.get(0);
            String docId = String.valueOf(firstHit.get("id"));
            double score = firstHit.get("score") instanceof Number n ? n.doubleValue() : Double.NaN;

            // Fetch document from KV by ID
            GetResult doc = collection.get(docId);
            JsonObject content = doc.contentAsObject();

            String question = content.getString("question");
            String answer = content.getString("answer");
            String image = content.containsKey("image") ? content.getString("image") : null;
            String link = content.containsKey("link") ? content.getString("link") : null;

            log.info("FTS+KV match: id={} score={} question='{}'", docId, score, question);

            return Optional.of(new MatchResult(question, answer, image, link, score));

        } catch (Exception e) {
            log.error("Error in FTS+KV vector search", e);
            return Optional.empty();
        }
    }

    /**
     * Result DTO for a matched FAQ entry.
     */
    public record MatchResult(String question, String answer,
                              String image, String link, double score) {}
}