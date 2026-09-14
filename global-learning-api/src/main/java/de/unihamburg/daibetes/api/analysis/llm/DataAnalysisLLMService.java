package de.unihamburg.daibetes.api.analysis.llm;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;


@Path("/data-analysis/llm")
@Produces("application/json")
@Consumes("application/json")
//@RolesAllowed("admin")
@Tag(name = "Model", description = "Service for managing app operations")
@Authenticated
public interface DataAnalysisLLMService {


    @PUT
    @Path("/{id}/files/{fileId}/analyze")
    @Operation(summary = "Starts a data analysis workflow using LLM")
    @APIResponse(responseCode = "200", description = "Data analysis workflow started")
    @APIResponse(responseCode = "404", description = "ModelWorkflowDetailDTO or File not found")
    @APIResponse(responseCode = "415", description = "File type not supported for LLM analysis")
    @Transactional
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<ResultAnalyzerResultDTO> analysisResult(@PathParam("id") Long id, @PathParam("fileId") Long fileId);
}
