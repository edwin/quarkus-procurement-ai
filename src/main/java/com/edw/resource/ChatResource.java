package com.edw.resource;

import com.edw.config.SemanticCacheStore;
import com.edw.service.ProcurementAssistant;
import com.edw.service.ProcurementAssistantHeavy;
import io.quarkus.websockets.next.*;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                                Multi.createFrom().item(cachedResponse),
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
}