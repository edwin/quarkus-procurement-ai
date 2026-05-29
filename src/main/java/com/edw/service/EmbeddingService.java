package com.edw.service;

import com.edw.model.ProcurementRecord;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    EmbeddingStore<TextSegment> store;

    @Transactional
    public void ingestBatch(int limit) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);

        List<ProcurementRecord> records = ProcurementRecord.find("embedded = false").page(0, limit).list();
        List<Future<?>> futures = new ArrayList<>();

        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> segments = new ArrayList<>();

        for (ProcurementRecord record : records) {

            futures.add(executor.submit(() -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id_rup", record.idRup);
                map.put("institution", record.institution);
                map.put("budget", record.budget.intValue());
                map.put("year", record.year.intValue());
                map.put("category", record.category);
                map.put("name", record.title);

                Metadata metadata = Metadata.from(map);

                String comprehensiveText = String.format(
                        "Judul Proyek (title) : %s. Instansi (institution) : %s. Tahun (year): %s. Kategori (category): %s. Budget atau anggaran: %s. Kode Proyek : %s",
                        record.title, record.institution, record.year, record.category, record.budget.intValue(), record.idRup
                );

                TextSegment segment = TextSegment.from(comprehensiveText, metadata);

                embeddings.add(embeddingModel.embed(segment).content());
                segments.add(segment);

                record.embedded = true;

                if(embeddings.size() % 10 == 0) {
                    log.info("Ingesting {} records", embeddings.size());
                    store.addAll(embeddings, segments);

                    embeddings.clear();
                    segments.clear();
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }

        // finish the remaining embeddings
        if(embeddings.size() > 0 && segments.size() > 0) {
            log.info("Ingesting {} records", embeddings.size());
            store.addAll(embeddings, segments);
        }
    }
}
