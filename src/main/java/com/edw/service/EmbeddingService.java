package com.edw.service;

import com.edw.model.ProcurementRecord;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    public void ingestBatch(int limit) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);

        List<ProcurementRecord> records = ProcurementRecord.find("embedded = false").page(0, limit).list();
        List<Future<?>> futures = new ArrayList<>();

        for (ProcurementRecord record : records) {

            futures.add(executor.submit(() -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id_rup", record.idRup);
                map.put("institution", record.institution);
                map.put("budget", record.budget.doubleValue());
                map.put("year", record.year.intValue());
                map.put("category", record.category);
                map.put("name", record.title);

                Metadata metadata = Metadata.from(map);

                String comprehensiveText = String.format(
                        "Judul Proyek (title) : %s. Instansi (institution) : %s. Tahun (year): %s. Kategori (category): %s. Budget atau anggaran: %s. Kode Proyek : %s",
                        record.title, record.institution, record.year, record.category, record.budget, record.idRup
                );

                TextSegment segment = TextSegment.from(comprehensiveText, metadata);
                store.add(embeddingModel.embed(segment).content(), segment);

                record.embedded = true;
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
    }
}
