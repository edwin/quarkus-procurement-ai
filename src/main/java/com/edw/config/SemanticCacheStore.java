package com.edw.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import io.quarkiverse.langchain4j.infinispan.InfinispanEmbeddingStore;
import io.quarkiverse.langchain4j.infinispan.runtime.InfinispanSchema;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class SemanticCacheStore {

    private static final double SIMILARITY_THRESHOLD = 0.90;

    @Inject
    RemoteCacheManager cacheManager;

    @Inject
    EmbeddingModel embeddingModel;

    private InfinispanEmbeddingStore store;

    private final Logger logger = LoggerFactory.getLogger(SemanticCacheStore.class);

    @PostConstruct
    void init() {
        String cacheXml = """
                <distributed-cache name="semantic-cache">
                    <expiration lifespan="3600000" max-idle="1800000"/>
                    <memory max-count="2000" when-full="REMOVE"/>
                    <indexing storage="local-heap">
                        <indexed-entities>
                            <indexed-entity>LangchainItem1024</indexed-entity>
                        </indexed-entities>
                    </indexing>
                </distributed-cache>
                """;

        store = InfinispanEmbeddingStore.builder()
                .cacheManager(cacheManager)
                .schema(new InfinispanSchema(
                        "semantic-cache",
                        1024L,
                        3,
                        "COSINE",
                        true,
                        cacheXml))
                .build();

        logger.info("Semantic cache store initialized");
    }

    /**
     * Retrieves a cached semantic response for the provided prompt by embedding the prompt,
     * searching for similar embeddings in the store, and returning the associated response if a match
     * exceeds the similarity threshold. If no suitable match is found, returns null.
     *
     * This operation runs asynchronously on a worker thread to avoid blocking.
     *
     * @param prompt the input prompt for which a cached response is retrieved
     * @return a semantic response associated with the prompt if a match is found with sufficient similarity,
     *         or null if no adequate match exists
     */
    public Uni<String> get(String prompt) {
        return Uni.createFrom().item(() -> {
            Embedding queryEmbedding = embeddingModel.embed(prompt).content();

            // Fetch best match at score 0.0 so we can log the actual score for tuning
            EmbeddingSearchResult<TextSegment> result = store.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(1)
                            .minScore(SIMILARITY_THRESHOLD)
                            .build());

            if (!result.matches().isEmpty()) {
                logger.debug("Cache HIT for {} (score={})",
                        prompt,
                        String.format("%.4f", result.matches().get(0).score()));
                return result.matches().get(0).embedded().text();
            }

            logger.debug("Cache MISS for: {}", prompt);
            return null;
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    /**
     * Asynchronously stores a semantic response for a given prompt by embedding the prompt, associating it with the response,
     * and adding it to the embedding store. This operation runs on a worker thread to avoid blocking.
     *
     * @param prompt the input prompt to be embedded and used as a key for storing the associated response
     * @param response the response to be stored in semantic cache, associated with the given prompt
     */
    public void putAsync(String prompt, String response) {
        Uni.createFrom().item(() -> {
                    Embedding embedding = embeddingModel.embed(prompt).content();
                    store.add(embedding, TextSegment.from(response));
                    logger.info("Semantic cache stored for prompt: {}", prompt);
                    return null;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .subscribe().with(
                        ignored -> {},
                        err -> logger.error("Failed to store in semantic cache for prompt '{}': {}",
                                prompt, err.getMessage())
                );
    }
}