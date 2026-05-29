package com.edw.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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

    @Produces
    @ApplicationScoped
    public RetrievalAugmentor retrievalAugmentor () {
        return DefaultRetrievalAugmentor.builder()
                .queryRouter(query -> {
                    String q = query.text().toLowerCase();
                    if (isAggregationQuery(q)) {
                        logger.debug("running database query for : {}", q);
                        return List.of();
                    }

                    EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                            .embeddingStore(store)
                            .embeddingModel(embeddingModel)
                            .maxResults(5)
                            .minScore(0.6)
                            .filter(isRegulationQuery(q) ?
                                        MetadataFilterBuilder.metadataKey("source-type")
                                                .isEqualTo("regulation") :
                                            MetadataFilterBuilder.metadataKey("source-type")
                                                    .isNotEqualTo("regulation")
                                    )
                            .build();

                    return List.of(retriever);
                })
                .build();
    }

    private boolean isAggregationQuery(String q) {
        return q.matches(".*(tampilkan|list|show|cari|berapa|terbesar|terkecil|" +
                "total|jumlah|terbanyak|ranking|urutan|top \\d|limit).*");
    }

    private boolean isRegulationQuery(String q) {
        return q.matches(".*(keputusan|peraturan|nomor|tahun|sk |regulasi|kebijakan|lkpp|aturan|dokumen|document).*");
    }
}
