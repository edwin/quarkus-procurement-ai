package com.edw.resource;

import com.edw.service.ProcurementAssistant;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
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

    private Logger logger = LoggerFactory.getLogger(ChatResource.class);

    @OnOpen
    String welcome() {
        return "Selamat Datang, nama saya Procurement-AI, apakah ada yang bisa saya bantu?";
    }

    @OnTextMessage
    public String ask(String question) {
        return assistant.chat(question);
    }
}
