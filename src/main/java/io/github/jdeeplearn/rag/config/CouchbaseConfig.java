package io.github.jdeeplearn.rag.config;

import com.couchbase.client.java.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Central Couchbase configuration.
 *
 * Uses the SDK 3.9 environment callback style so the SDK manages the
 * ClusterEnvironment lifecycle. We just configure timeouts and reuse
 * the Cluster / Bucket / Collection as singletons.
 */
@Configuration
public class CouchbaseConfig {

    private static final Logger log = LoggerFactory.getLogger(CouchbaseConfig.class);

    @Bean(destroyMethod = "disconnect")
    public Cluster couchbaseCluster(
            @Value("${couchbase.connection-string}") String connectionString,
            @Value("${couchbase.username}") String username,
            @Value("${couchbase.password}") String password
    ) {
        log.info("Connecting to Couchbase at '{}'", connectionString);

        return Cluster.connect(
                connectionString,
                ClusterOptions.clusterOptions(username, password)
                        .environment(env -> env
                                .timeoutConfig(timeout -> timeout
                                        .connectTimeout(Duration.ofSeconds(5))
                                        .kvTimeout(Duration.ofSeconds(2))
                                        .queryTimeout(Duration.ofSeconds(5))
                                )
                        )
        );
    }

    @Bean
    public Bucket faqBucket(
            Cluster cluster,
            @Value("${couchbase.bucket}") String bucketName
    ) {
        Bucket bucket = cluster.bucket(bucketName);
        bucket.waitUntilReady(Duration.ofSeconds(10));
        log.info("Couchbase bucket '{}' is ready", bucketName);
        return bucket;
    }


    @Bean
    public Scope faqScope(
            Cluster cluster,
            @Value("${couchbase.bucket}") String bucketName,
            @Value("${couchbase.scope}") String scopeName) {
        Bucket bucket = cluster.bucket(bucketName);
        // Ensure bucket is ready before accessing the scope
        bucket.waitUntilReady(java.time.Duration.ofSeconds(10));
        return bucket.scope(scopeName);
    }

    @Bean
    public Collection faqCollection(Scope faqScope,
            @Value("${couchbase.collection:faqs}") String collectionName
    ) {
        log.info("Using scope='{}', collection='{}' for FAQ documents", faqScope.name(), collectionName);
        return faqScope.collection(collectionName);
    }
}
