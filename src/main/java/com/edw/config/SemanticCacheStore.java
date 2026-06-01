package com.edw.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import io.quarkiverse.langchain4j.infinispan.InfinispanEmbeddingStore;
import io.quarkiverse.langchain4j.infinispan.runtime.InfinispanSchema;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <pre>
 *  com.edw.config.SemanticCacheStore
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 01 Jun 2026 14:10
 */
@ApplicationScoped
public class SemanticCacheStore {

    @Inject
    RemoteCacheManager cacheManager;

    @Inject
    EmbeddingModel embeddingModel;

    private InfinispanEmbeddingStore store;

    private Logger logger = LoggerFactory.getLogger(SemanticCacheStore.class);

    @PostConstruct
    void init() {

        String cacheXml = """
                <distributed-cache name="semantic-cache">
                    <expiration lifespan="3600000" max-idle="1800000"/>
                    <memory max-count="500" when-full="REMOVE"/>
                    <indexing storage="local-heap">
                        <indexed-entities>
                            <indexed-entity>LangchainItem1024</indexed-entity>
                        </indexed-entities>
                    </indexing>
                </distributed-cache>
                """;

        store = InfinispanEmbeddingStore.builder()
                .cacheManager(cacheManager)
                .schema(new InfinispanSchema("semantic-cache",
                                                    1024L,
                                                    3,
                                                    "COSINE",
                                                    true,
                                                    cacheXml))
                .build();
    }

    public Uni<String> get(String prompt) {
        return Uni.createFrom().item(() -> {
            Embedding queryEmbedding = embeddingModel.embed(prompt).content();
            EmbeddingSearchResult<TextSegment> result = store.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(1)
                            .minScore(0.90)
                            .build());

            if (!result.matches().isEmpty()) {
                return result.matches().get(0).embedded().text();
            }
            return null;
        }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }

    public void putAsync(String prompt, String response) {
        Uni.createFrom().item(() -> {
                    Embedding embedding = embeddingModel.embed(prompt).content();
                    store.add(embedding, TextSegment.from(response));
                    logger.info("semantic cache stored for prompt: {}", prompt);
                    return null;
                })
                .runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool())
                .subscribe().with(
                        ignored -> {},
                        err -> logger.error("Failed to store in semantic cache: {}", err.getMessage())
                );
    }

}
