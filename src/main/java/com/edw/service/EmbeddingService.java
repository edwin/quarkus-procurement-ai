package com.edw.service;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import com.edw.model.ProcurementRecord;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.docling.runtime.client.DoclingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

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

    private static final int EMBED_BATCH_SIZE = 32;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    EmbeddingStore<TextSegment> store;

    @Inject
    DoclingService doclingService;

    @Inject
    DoclingServeApi doclingServeApi;

    /**
     * Processes procurement records in batch. Retrieves a specified number of records
     * that have not been embedded, generates their embeddings using a configured
     * embedding model, associates them with metadata, and stores them in the embedding store.
     * Records are marked as embedded after processing.
     *
     * @param limit the maximum number of records to process in a single batch
     * @throws Exception if an error occurs during processing
     */
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

    public void ingestPdf() throws Exception {
        Path documentsPath = Path.of("pdf/");
        List<String> allowedExtensions = Arrays.asList("pdf");

        List<Document> docs = new ArrayList<>();

        if (Files.exists(documentsPath) && Files.isDirectory(documentsPath)) {
            try (Stream<Path> stream = Files.list(documentsPath)) {
                for (Path filePath : stream.filter(Files::isRegularFile).toList()) {
                    String fileName = filePath.getFileName().toString();
                    String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

                    if(allowedExtensions.contains(extension)) {

                        log.info("Processing file: {}", fileName);

                        ConvertDocumentRequest convertDocumentRequest = convertDocumentRequest(new File(documentsPath.toString() + "/" + fileName));
                        ConvertDocumentResponse convertDocumentResponse =  doclingServeApi.convertSource(convertDocumentRequest);
                        InBodyConvertDocumentResponse inBodyConvertDocumentResponse = (InBodyConvertDocumentResponse) convertDocumentResponse;
                        String markdown = inBodyConvertDocumentResponse.getDocument().getMarkdownContent();

                        // clean the markdown
                        markdown = markdown.replaceAll("!\\[.*?\\]\\(data:image/[^)]+\\)", "");

                        Map<String, String> meta = new HashMap<>();
                        meta.put("file", fileName);
                        meta.put("format", extension);

                        docs.add(Document.document(markdown, Metadata.from(meta)));
                    }
                }
            }
        }

        // store the documents
        embedAndStore(docs);
        log.info("Ingesting {} documents", docs.size());
    }

    private ConvertDocumentRequest convertDocumentRequest(File sourceFile) throws IOException {
        try {
            byte[] bytes = Files.readAllBytes(sourceFile.toPath());
            String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
            return ConvertDocumentRequest.builder()
                    .source(FileSource.builder()
                            .filename(sourceFile.getName())
                            .base64String(base64)
                            .build())
                    .options(ConvertDocumentOptions.builder()
                            .includeImages(false)
                            .build())
                    .build();
        } catch (IOException e) {
            throw new IOException("Failed to read local file: " + sourceFile, e);
        }
    }

    private void embedAndStore(List<Document> docs) {
        DocumentBySentenceSplitter splitter = new DocumentBySentenceSplitter(200, 20);
        List<TextSegment> segments = splitter.splitAll(docs);

        if (segments.isEmpty()) {
            log.info("no segments found from documents.");
            return;
        }

        for (int start = 0; start < segments.size(); start += EMBED_BATCH_SIZE) {
            int end = Math.min(start + EMBED_BATCH_SIZE, segments.size());
            List<TextSegment> batch = new ArrayList<>(segments.subList(start, end));
            try {
                List<Embedding> embeddings = embeddingModel.embedAll(batch).content();
                if (embeddings == null || embeddings.size() != batch.size()) {
                    throw new IllegalStateException("embedAll returned "
                            + (embeddings == null ? 0 : embeddings.size())
                            + " embeddings for batch size "
                            + batch.size());
                }
                store.addAll(embeddings, batch);
            } catch (Exception e) {
                log.error("failing due to : {} ", e.getMessage(), e);
            }
        }

        log.info("finish ingesting {} documents", docs.size());
    }
}
