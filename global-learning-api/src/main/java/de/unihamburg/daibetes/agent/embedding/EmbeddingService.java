package de.unihamburg.daibetes.agent.embedding;

import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;


@Path("/embeddings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "Embeddings", description = "Service for testing embeddings")
public interface EmbeddingService {

    @GET
    @Path("/model/{modelId}/has-embedding")
    @Operation(summary = "Check if Model has an Embedding")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Has Embedding"),
            @APIResponse(responseCode = "204", description = "Dont has Embedding"),
            @APIResponse(responseCode = "404", description = "Paper not found")
    })
    @Transactional
    Response hasModelEmbedding(@PathParam("modelId") Long modelId);

    @GET
    @Path("/app/{appId}/has-embedding")
    @Operation(summary = "Check if App has an Embedding")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Has Embedding"),
            @APIResponse(responseCode = "204", description = "Dont has Embedding"),
            @APIResponse(responseCode = "404", description = "Paper not found")
    })
    @Transactional
    Response hasAppEmbedding(@PathParam("appId") Long appId);

    @GET
    @Path("/model/{modelId}")
    @Operation(summary = "Get all Embeddings for a Model")
    @APIResponse(responseCode = "200", description = "List of Embeddings",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = EmbeddingDTO.class))
    )
    @Transactional
    List<EmbeddingDTO> getAllEmbeddingsForModel(@PathParam("modelId") Long modelId);

    @GET
    @Path("/app/{appId}")
    @Operation(summary = "Get all Embeddings for a App")
    @APIResponse(responseCode = "200", description = "List of Embeddings",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = EmbeddingDTO.class))
    )
    @Transactional
    List<EmbeddingDTO> getAllEmbeddingsForApp(@PathParam("appId") Long appId);


    @GET
    @Path("/query")
    @Operation(summary = "Query Embeddings")
    @APIResponse(responseCode = "200", description = "Embedding Query Results")
    @Transactional
    List<EmbeddingAugmentorResultDTO> queryEmbedding(@QueryParam("query") String query);

    @GET
    @Path("/rag")
    @Produces(MediaType.TEXT_PLAIN)
    Response answerChat(@QueryParam("chat") String chat);
}
