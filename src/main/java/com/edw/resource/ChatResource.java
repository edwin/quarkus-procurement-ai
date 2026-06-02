package com.edw.resource;

import com.edw.config.SemanticCacheStore;
import com.edw.service.ProcurementAssistant;
import com.edw.service.ProcurementAssistantHeavy;
import io.quarkus.websockets.next.*;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@WebSocket(path = "/procurement/chat")
public class ChatResource {

    @Inject
    ProcurementAssistant assistant;

    @Inject
    ProcurementAssistantHeavy heavyAssistant;

    @Inject
    WebSocketConnection webSocketConnection;

    @Inject
    SemanticCacheStore semanticCache;

    private Logger logger = LoggerFactory.getLogger(ChatResource.class);

    @OnOpen
    String welcome() {
        return "Selamat Datang, nama saya Procurement-AI, apakah ada yang bisa saya bantu?";
    }

    @OnClose
    public void onClose() {
        logger.debug("WebSocket closed: {}", webSocketConnection.id());
    }

    @OnTextMessage
    public Multi<String> ask(String question) {
        logger.debug("question : {}", question);

        String memoryId = webSocketConnection.id();

        // heavy model
        if (needsHeavyModel(question.toLowerCase())) {
            return Multi.createBy().concatenating().streams(
                    heavyAssistant.chat(question, memoryId),
                    Multi.createFrom().item("[DONE]")
            ).onFailure().recoverWithItem(err -> {
                logger.error("Error occurred while processing question: {}", question, err);
                return "[ERROR] Something went wrong: please contact support.";
            });
        }

        // light model - first check on cache and them LLM
        return semanticCache.get(question)
                .onItem().transformToMulti(cachedResponse -> {
                    if (cachedResponse != null) {
                        logger.debug("Cache HIT for: {}", question);
                        return Multi.createBy().concatenating().streams(
                                streamCachedResponse(cachedResponse),
                                Multi.createFrom().item("[DONE]")
                        );
                    }

                    logger.debug("Cache MISS for: {}", question);

                    // Tap into the single stream — no second subscription
                    StringBuilder fullResponse = new StringBuilder();
                    Multi<String> response = assistant.chat(question, memoryId)
                            .invoke(token -> fullResponse.append(token));

                    return Multi.createBy().concatenating().streams(
                            response,
                            Multi.createFrom().item("[DONE]")
                    ).onCompletion().invoke(() -> {
                        String full = fullResponse.toString();
                        if (!full.isEmpty() && !full.startsWith("[ERROR]")) {
                            semanticCache.putAsync(question, full);
                        }
                    });
                })
                .onFailure().recoverWithItem(err -> {
                    logger.error("Error occurred while processing question: {}", question, err);
                    return "[ERROR] Something went wrong: please contact support.";
                });
    }

    private boolean needsHeavyModel(String q) {
        return q.matches(".*(analisa|analisis|evaluasi).*");
    }

    /**
     * Streams a cached response by splitting the given response into words, appending a space to each word,
     * and delaying the emission of each word by 40 milliseconds.
     *
     * @param response the cached response string to be streamed, which is split into individual words
     * @return a Multi emitting the words of the response one-by-one, each with a 40-millisecond delay
     */
    private Multi<String> streamCachedResponse(String response) {
        String[] words = response.split("\\s+");

        return Multi.createFrom().items(words)
                .onItem().transform(word -> word + " ")
                .onItem().call(word ->
                        Uni.createFrom().voidItem()
                                .onItem().delayIt().by(Duration.ofMillis(40))
                );
    }
}