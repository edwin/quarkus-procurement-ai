package com.edw.resource;

import com.edw.service.EmbeddingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * <pre>
 *  com.edw.resource.IngestResource
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 01 May 2026 20:20
 */
@Path("/procurement")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IngestResource {

    @Inject
    EmbeddingService embeddingService;

    @POST
    @Path("/ingest")
    public void train(@QueryParam("limit") int limit) throws Exception {
        embeddingService.ingestBatch(limit);
    }

}
