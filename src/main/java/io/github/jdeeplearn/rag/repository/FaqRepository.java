package io.github.jdeeplearn.rag.repository;

import com.couchbase.client.core.error.CouchbaseException;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.kv.GetOptions;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.search.SearchOptions;
import com.couchbase.client.java.search.SearchQuery;
import com.couchbase.client.java.search.SearchRequest;
import com.couchbase.client.java.search.result.SearchResult;
import com.couchbase.client.java.search.result.SearchRow;
import com.couchbase.client.java.search.vector.VectorQuery;
import com.couchbase.client.java.search.vector.VectorSearch;
import io.github.jdeeplearn.rag.dto.FaqResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Repository responsible for talking to Couchbase Search Vector Index
 * to find the best FAQ matches for a given embedding (and optional keyword).
 * <p>
 * This implementation uses Couchbase Java SDK 3.9.x Search APIs:
 * - {@link SearchRequest}
 * - {@link VectorSearch}
 * - {@link VectorQuery}
 * - {@link SearchOptions}
 * <p>
 * It does NOT use SQL++ + TEXT_SCORE / VECTOR_DISTANCE anymore,
 * because those are brittle and caused runtime / parsing failures.
 */
@Repository
public class FaqRepository {

    private static final Logger log = LoggerFactory.getLogger(FaqRepository.class);

    private final Collection faqCollection;
    private final Cluster cluster;
    private final String searchIndexName;
    private final String vectorFieldName;
    private final int limit;          // How many hits we actually retrieve
    private final int numCandidates;  // How many candidates vector search considers

    public FaqRepository(
            Cluster cluster,
            Collection faqCollection,
            @Value("${faq.search.index-name}") String searchIndexName,
            @Value("${faq.search.vector-field-name:question_vector}") String vectorFieldName,
            @Value("${faq.search.limit:5}") int limit,
            @Value("${faq.search.num-candidates:50}") int numCandidates
    ) {
        this.cluster = Objects.requireNonNull(cluster, "cluster must not be null");
        this.faqCollection = Objects.requireNonNull(faqCollection, "faqCollection must not be null");
        this.searchIndexName = Objects.requireNonNull(searchIndexName, "searchIndexName must not be null");
        this.vectorFieldName = Objects.requireNonNull(vectorFieldName, "vectorFieldName must not be null");
        this.limit = limit;
        this.numCandidates = numCandidates;
    }

    /**
     * Top-K “hybrid” search:
     * - Always runs a Vector Search over {@code vectorFieldName}.
     * - If {@code keyword} is not blank, also adds an FTS query on the question text.
     * <p>
     * Couchbase merges scores internally and exposes a single {@link SearchRow#score()}.
     *
     * @param queryVector embedding for the user question (length must match index dims).
     * @param keyword     optional keyword / question text for lexical FTS; can be null/blank.
     * @return ordered list of matches, highest score first (may be empty).
     */
    public List<FaqMatch> findTopKHybrid(float[] queryVector, String keyword) {
        if (queryVector == null || queryVector.length == 0) {
            log.warn("findTopKHybrid called with empty queryVector; returning no matches.");
            return List.of();
        }

        try {
            // 1) Build the VectorSearch part (required)
            VectorQuery vectorQuery = VectorQuery
                    .create(vectorFieldName, queryVector)
                    .numCandidates(numCandidates);

            VectorSearch vectorSearch = VectorSearch.create(vectorQuery);

            // 2) Build SearchRequest – vector only, or vector + FTS
            SearchRequest request;

            if (keyword != null && !keyword.isBlank()) {
                // Very simple lexical query on "question" field
                // You can swap to SearchQuery.match(...) if you prefer.
                var ftsQuery = SearchQuery.queryString(keyword);
                request = SearchRequest
                        .create(ftsQuery)          // FTS part
                        .vectorSearch(vectorSearch); // plus vector
            } else {
                request = SearchRequest.create(vectorSearch);
            }

            // 3) Execute search against the vector index
            SearchOptions options = SearchOptions
                    .searchOptions()
                    .limit(limit)
                    .fields("question", "answer", "image", "link");

            SearchResult result = cluster.search(searchIndexName, request, options);

            // 4) Map rows → FaqMatch
            List<FaqMatch> matches = new ArrayList<>();

            for (SearchRow row : result.rows()) {
                matches.add(new FaqMatch(
                        row.id(),
                        row.score()
                ));
            }

            // 5) Sort highest score first, just in case Search didn’t already
            matches.sort(Comparator.comparingDouble(FaqMatch::score).reversed());

            var metrics = result.metaData().metrics();
            if (metrics != null && log.isDebugEnabled()) {
                log.debug("FTS vector search: hits={} maxScore={} took={}us",
                        metrics.totalRows(),
                        metrics.maxScore(),
                        metrics.took());
            }

            return matches;
        } catch (CouchbaseException ex) {
            log.error("FTS vector search failed for index='{}': {}", searchIndexName, ex, ex);
            return List.of();
        }
    }

    /**
     * Simple value object for a FAQ match.
     * Records are concise, immutable, and great for this use-case.
     */
    public record FaqMatch(
            String id,
            double score
    ) {
    }

    public FaqResponse getFaqDocumentById(String id) {
        GetResult result = faqCollection.get(id, GetOptions.getOptions().project(List.of("answer", "image", "link")));
        return result.contentAs(FaqResponse.class);
    }
}
