package com.edw.service;

import com.edw.model.ProcurementRecord;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

            Map<String, Object> map = new HashMap<>();
            map.put("id_rup", record.idRup);
            map.put("institution", record.institution);
            map.put("budget", record.budget.doubleValue());
            map.put("year", record.year);

            Metadata metadata = Metadata.from(map);

            String comprehensiveText = String.format(
                    "Judul Proyek: %s. Instansi: %s. Tahun: %s. Kategori: %s.",
                    record.title, record.institution, record.year, record.category
            );

            TextSegment segment = TextSegment.from(comprehensiveText, metadata);
            store.add(record.getEmbeddingId().toString(), embeddingModel.embed(segment).content());

            record.embedded = true;
        }
    }
}
