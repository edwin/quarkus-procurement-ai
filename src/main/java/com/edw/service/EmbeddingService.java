package com.edw.service;

import com.edw.model.ProcurementRecord;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * <pre>
 *  com.edw.service.EmbeddingService
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 28 Apr 2026 15:47
 */
@ApplicationScoped
public class EmbeddingService {

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    EmbeddingStore<TextSegment> store;

    @Transactional
    public void ingestBatch(int limit) {
        List<ProcurementRecord> records = ProcurementRecord.find("embedded = false").page(0, limit).list();

        for (ProcurementRecord record : records) {
            TextSegment segment = TextSegment.from(record.title);
            store.add(embeddingModel.embed(segment).content(), segment);
            record.embedded = true;
        }
    }
}
