package de.unihamburg.daibetes.api.app.validation;

import bio.cosy.feddb.core.api.app.config.ToolConfigDTO;
import bio.cosy.feddb.core.base.BaseValidationResultDTO;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;


@Path("/apps/validation")
@Produces("application/json")
@Consumes("application/json")
//@RolesAllowed("admin")
@Tag(name = "Tool Validation", description = "Service for managing tool input validation")
public interface ToolInputValidationService {


    @POST
    @Path("/hyperparam")
    @Operation(summary = "Publish an App")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Validated")
    })
    @Authenticated
    BaseValidationResultDTO validateHyperParamValues(@RequestBody @Valid ToolHyperParamConfigValidateRequestDTO config);


    @POST
    @Path("/file/{fileId}")
    @Operation(summary = "Publish an App")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Validated")
    })
    @Authenticated
    @Transactional
    BaseValidationResultDTO validateFile(@PathParam("fileId") Long fileId, @RequestBody @Valid ToolConfigDTO config);

}
