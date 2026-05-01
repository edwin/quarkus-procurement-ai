package com.edw.resource;

import com.edw.service.EmbeddingService;
import com.edw.service.ProcurementAssistant;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
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
@Path("/procurement")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatResource {

    @Inject
    ProcurementAssistant assistant;

    @Inject
    EmbeddingService embeddingService;

    private Logger logger = LoggerFactory.getLogger(ChatResource.class);

    @POST
    @Path("/chat")
    public String ask(String question) {
        return assistant.chat(question);
    }

    @POST
    @Path("/ingest")
    public void train(@QueryParam("limit") int limit) {
        embeddingService.ingestBatch(limit);
    }

}
