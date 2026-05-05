package com.edw.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * <pre>
 *  com.edw.config.RagConfiguration
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 29 Apr 2026 17:38
 */
@ApplicationScoped
public class RagConfiguration {

    @Inject
    EmbeddingStore<TextSegment> store;

    @Inject
    EmbeddingModel embeddingModel;

    private Logger logger = LoggerFactory.getLogger(RagConfiguration.class);

    private static final Pattern ANALYTICAL_PATTERN = Pattern.compile(
            ".*(rata.rata|terbesar|terkecil|tertinggi|terendah|total|jumlah|" +
                    "berapa|maksimum|minimum|ranking|peringkat|terbanyak|average|" +
                    "avg|max|min|sum|count|bandingkan|perbandingan|besar|kecil).*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    @Produces
    @ApplicationScoped
    public RetrievalAugmentor retrievalAugmentor () {
        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(10)
                .minScore(0.3)
                .build();

        QueryRouter router = new QueryRouter() {
            @Override
            public Collection<ContentRetriever> route(Query query) {
                String text = query.text().toLowerCase();
                if (ANALYTICAL_PATTERN.matcher(text).matches()) {
                    logger.debug("[Router] Analytical query -> using Tools");
                    return Collections.emptyList();
                }

                logger.debug("[Router] Semantic query -> using RAG");
                return List.of(retriever);
            }
        };

        return DefaultRetrievalAugmentor.builder()
                .queryRouter(router)
                .build();
    }

}
