package de.unihamburg.daibetes.api.model.sub.result;

import bio.cosy.feddb.core.api.model.result.ModelExperimentResultBaseService;
import bio.cosy.feddb.core.api.model.result.ModelExperimentResultDTO;
import de.unihamburg.daibetes.api.feddbclient.FLNetClientAuthenticated;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/model/result")
@Produces("application/json")
@Consumes("application/json")
@Tag(name = "Model", description = "Service for managing app operations")
public interface ModelExperimentResultService extends ModelExperimentResultBaseService {

    @POST
    @Path("/experiment/upload")
    @Operation(summary = "Upload many files with metadata")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Files uploaded"),
            @APIResponse(responseCode = "400", description = "Invalid request"),
            @APIResponse(responseCode = "401", description = "Authentication required when FLNet client auth is enabled"),
            @APIResponse(responseCode = "500", description = "Upload failed")
    })
    @FLNetClientAuthenticated
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    Response upload(@BeanParam ModelExperimentResultDTO req);
}
