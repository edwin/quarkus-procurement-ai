package com.edw.resource;

import com.edw.service.ProcurementAssistant;
import io.quarkus.websockets.next.*;
import io.smallrye.mutiny.Multi;
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
    WebSocketConnection webSocketConnection;

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
        logger.debug("question : {} ",question);
        return Multi.createBy().concatenating().streams(
                assistant.chat(question)
                    .onItem().transform(token -> token),
                Multi.createFrom().item("[DONE]")
        )
        .onFailure().recoverWithItem(err -> "[ERROR] " + err.toString());
    }
}
