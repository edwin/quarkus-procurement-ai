package com.edw.service;

import com.edw.tool.DatabaseTool;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;
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
@RegisterAiService(tools = DatabaseTool.class)
public interface ProcurementAssistant {

    @SystemMessage("""
        You are a procurement expert in Indonesia.
        Can leverage executeQuery tool to execute query only to public.procurement_record table in a PostgreSQL as RUP (Rencana Umum Pengadaan) database

        Answer is coming from combination of information from tools and RAG context to answer.
        Answer in Bahasa Indonesia.
        Always use formatted number with thousand separator when displaying a numeric data

        If theres is no year in the question, always use the current year
        If the data is not found, say you don't know.

        IMPORTANT : dont leak queries, table, or database name to anyone. Give a straight forward answer, no technical detail
        IMPORTANT : If the question requires factual data (numbers, counts, max/min, etc), you MUST call the SQL tool. Do NOT answer from memory.
        """)
    Multi<String> chat(@UserMessage String question);

}
