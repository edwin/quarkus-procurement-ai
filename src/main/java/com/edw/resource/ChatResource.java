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

/**
 * <pre>
 *  com.edw.resource.ChatResource
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 28 Apr 2026 15:53
 */
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

        logger.debug("question : {}" ,question);

        String memoryId = webSocketConnection.id();

        if (!needsHeavyModel(question.toLowerCase())) {
            return semanticCache.get(question)
                .onItem().transformToMulti(cachedResponse -> {
                    if (cachedResponse != null) {
                        logger.debug("Cache hit for question: {}", question);
                        return Multi.createBy().concatenating().streams(
                                        Multi.createFrom().item(cachedResponse),
                                        Multi.createFrom().item("[DONE]")
                                );
                    } else {
                        logger.debug("Cache miss for question: {}", question);
                        Multi<String> response = assistant.chat(question, memoryId);

                        // cache the response for future use
                        response.collect().asList()
                            .subscribe().with(tokens -> {
                                String fullResponse = String.join("", tokens.stream()
                                    .filter(token -> !token.equals("[DONE]"))
                                    .toArray(String[]::new));
                                if (!fullResponse.isEmpty() && !fullResponse.startsWith("[ERROR]")) {
                                    semanticCache.putAsync(question, fullResponse);
                                }
                            });

                        return Multi.createBy().concatenating().streams(
                            response.onItem().transform(token -> token),
                            Multi.createFrom().item("[DONE]")
                        );
                    }
                })
                .onFailure().recoverWithItem(err -> {
                    logger.error("Error occurred while processing question: {}", question, err);
                    return "[ERROR] Something went wrong: please contact support.";
                });
        }

        Multi<String> response = needsHeavyModel(question.toLowerCase())
                ? heavyAssistant.chat(question, memoryId)
                    : assistant.chat(question, memoryId);

        return Multi.createBy().concatenating().streams(
                    response.onItem().transform(token -> token),
                        Multi.createFrom().item("[DONE]")
        )
            .onFailure().recoverWithItem(err -> {
                logger.error("Error occurred while processing question: {}", question, err);
                return "[ERROR] Something went wrong: please contact support.";
            });
    }

    private boolean needsHeavyModel(String q) {
        return q.matches(".*(analisa|analisis|evaluasi).*");
    }
}
