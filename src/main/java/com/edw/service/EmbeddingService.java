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
    DoclingServeApi doclingServeApi;

    /**
     * Ingests a batch of procurement records and processes their embeddings.
     *
     * This method retrieves a batch of procurement records with a specified limit,
     * processes them to generate text segments and corresponding embeddings,
     * and stores the embeddings. Records that are successfully processed will
     * have their "embedded" status updated to true.
     *
     * The processing operates asynchronously, utilizing a fixed thread pool
     * for improved performance.
     *
     * @param limit the number of procurement records to process in a single batch
     * @throws Exception if an error occurs during the processing or embedding of records
     */
    @Transactional
    public void ingestBatch(int limit) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);

        List<ProcurementRecord> records = ProcurementRecord.find("embedded = false")
                .page(0, limit).list();

        List<Future<Map.Entry<Embedding, TextSegment>>> futures = new ArrayList<>();

        for (ProcurementRecord record : records) {
            futures.add(executor.submit(() -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id_rup",        record.idRup);
                map.put("institution",   record.institution);
                map.put("budget",        record.budget.intValue());
                map.put("year",          record.year.intValue());
                map.put("category",      record.category);
                map.put("name",          record.title);

                String comprehensiveText = String.format(
                        "Judul Proyek (title) : %s. Instansi (institution) : %s. " +
                                "Tahun (year): %s. Kategori (category): %s. " +
                                "Budget atau anggaran: %s. Kode Proyek : %s",
                        record.title, record.institution, record.year,
                        record.category, record.budget.intValue(), record.idRup
                );

                TextSegment segment = TextSegment.from(comprehensiveText, Metadata.from(map));
                Embedding embedding  = embeddingModel.embed(segment).content();

                return Map.entry(embedding, segment);
            }));
        }

        List<Embedding>   embeddings = new ArrayList<>(records.size());
        List<TextSegment> segments   = new ArrayList<>(records.size());

        for (int i = 0; i < futures.size(); i++) {
            Map.Entry<Embedding, TextSegment> result = futures.get(i).get();
            embeddings.add(result.getKey());
            segments.add(result.getValue());

            records.get(i).embedded = true;
        }

        for (int start = 0; start < embeddings.size(); start += EMBED_BATCH_SIZE) {
            int end = Math.min(start + EMBED_BATCH_SIZE, embeddings.size());
            List<Embedding>   batchEmb = new ArrayList<>(embeddings.subList(start, end));
            List<TextSegment> batchSeg = new ArrayList<>(segments.subList(start, end));

            log.info("Ingesting records {} to {}", start, end);
            store.addAll(batchEmb, batchSeg);
        }

        executor.shutdown();
    }

    /**
     * Processes and ingests PDF files from a predefined directory.
     *
     * This method scans the "pdf/" directory for files with the ".pdf" extension.
     * For each valid file, it:
     * - Converts the file content into markdown using an external service.
     * - Cleans up the markdown by removing inline images (e.g., base64 image data).
     * - Enriches the processed content with metadata, including the file name and format.
     * - Creates a list of `Document` objects from the processed and enriched data.
     *
     * Finally, the documents are embedded and stored using the configured embedding service.
     * A log message provides information on the number of successfully ingested documents.
     *
     * @throws Exception If any error occurs during the processing or embedding of the documents.
     */
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
                        markdown = markdown.replaceAll("(?<=\\b\\w) (?=\\w\\b)", "");
                        markdown = markdown.replaceAll("\\n{3,}", "\n\n");
                        markdown = markdown.replaceAll("<!--.*?-->", "");

                        Map<String, String> meta = new HashMap<>();
                        meta.put("file", fileName);
                        meta.put("format", extension);
                        meta.put("source-type", "regulation");

                        docs.add(Document.document(markdown, Metadata.from(meta)));
                    }
                }
            }
        }

        // store the documents
        embedAndStore(docs);
        log.info("Ingesting {} documents", docs.size());
    }

    /**
     * Converts a source file into a {@code ConvertDocumentRequest} object.
     * This method reads the content of the provided file, encodes it in base64 format,
     * and builds a {@code ConvertDocumentRequest} instance with the necessary options.
     *
     * @param sourceFile the file to be converted into a {@code ConvertDocumentRequest}
     * @return a {@code ConvertDocumentRequest} instance containing the file's content and metadata
     * @throws IOException if an error occurs while reading the file
     */
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

    /**
     * Embeds text segments created from the provided list of documents and stores the resulting embeddings.
     *
     * This method splits the content of the provided documents into smaller text segments using a
     * configured {@code DocumentBySentenceSplitter}. The segments are processed in batches to generate embeddings
     * which are then stored along with their corresponding text segments. If any error occurs during the
     * embedding process, the issue is logged, and processing continues for the remaining batches.
     *
     * @param docs the list of {@code Document} objects to be embedded and stored
     */
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
