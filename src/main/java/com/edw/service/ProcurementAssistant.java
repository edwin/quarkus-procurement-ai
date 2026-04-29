package com.edw.service;

import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * <pre>
 *  com.edw.service.ProcurementAssistant
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 28 Apr 2026 15:43
 */
@RegisterAiService
@ApplicationScoped
public interface ProcurementAssistant {

    @SystemMessage("""
        You are a procurement expert in Indonesia.
        Answer only using the provided context from the RUP (Rencana Umum Pengadaan) database to answer.
        Answer in Bahasa Indonesia.
        If the data is not in the context, say you don't know.
        """)
    String chat(@UserMessage String question);

}
