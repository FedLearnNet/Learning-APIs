package de.unihamburg.daibetes.api.model;

import bio.cosy.feddb.core.api.model.ModelDTO;
import bio.cosy.feddb.core.api.model.ModelDetailDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisCreatePredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import bio.cosy.feddb.core.base.ValidationGroups;
import bio.cosy.feddb.core.api.model.ModelSubDTO;
import bio.cosy.feddb.core.api.model.ModelVersionDTO;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.groups.ConvertGroup;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;


@Path("/model")
@Produces("application/json")
@Consumes("application/json")
//@RolesAllowed("admin")
@Tag(name = "Model", description = "Service for managing app operations")
@Authenticated
public interface ModelService {

    @GET
    @Operation(summary = "Lists all models")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all apps")
    })
    @Transactional
    List<ModelDTO> list();

    @GET
    @Path("/my")
    @Operation(summary = "Lists all user apps")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all apps")
    })
    @Transactional
    List<ModelDTO> listMyModels(@QueryParam("appId") Long appId,
                                @QueryParam("experimentId") Long experimentId,
                                @QueryParam("federatedExperimentId") Long federatedExperimentId);

    @POST
    @Operation(summary = "Lists all user apps")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "created model")
    })
    @Transactional
    ModelVersionDTO createForImage(ModelCreateForImageDTO request);

    @GET
    @Path("/experiment/run/{id}")
    @Operation(summary = "Lists Model for experiment run")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all apps")
    })
    @Transactional
    ModelSubDTO retrieveForExperimentRun(@PathParam("id") Long id);

    @GET
    @Path("/experiment/federated/run/{id}")
    @Operation(summary = "Lists Model for experiment run")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all apps")
    })
    @Transactional
    ModelSubDTO retrieveForFederatedExperimentRun(@PathParam("id") Long id);

    @GET
    @Path("/experiment/{id}")
    @Operation(summary = "Lists Model for experiment")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all apps")
    })
    @Transactional
    ModelVersionDTO retrieveForExperiment(@PathParam("id") Long id);

    @GET
    @Path("/experiment/federated/{id}")
    @Operation(summary = "Lists Model for experiment")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all apps")
    })
    @Transactional
    ModelVersionDTO retrieveForFederatedExperiment(@PathParam("id") Long id);

    @GET
    @Path("/{id}")
    @Operation(summary = "Retrieve a single app")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    ModelDetailDTO retrieve(@PathParam("id") Long id);

    @PUT
    @Path("/{id}")
    @Operation(summary = "Updates a model")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "model updated"),
            @APIResponse(responseCode = "400", description = "Invalid data"),
            @APIResponse(responseCode = "404", description = "model not found")
    })
    @Transactional
    ModelDTO update(@PathParam("id") Long id, @Valid @ConvertGroup(to = ValidationGroups.Put.class) @RequestBody ModelDTO dto);

    @PUT
    @Path("/{id}/version/{modelVersionId}")
    @Operation(summary = "Updates a model version")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "model updated"),
            @APIResponse(responseCode = "400", description = "Invalid data"),
            @APIResponse(responseCode = "404", description = "model not found")
    })
    @Transactional
    ModelVersionDTO update(@PathParam("id") Long id, @PathParam("modelVersionId") Long modelVersionId, @Valid @ConvertGroup(to = ValidationGroups.Put.class) @RequestBody ModelVersionDTO dto);


    @DELETE
    @Path("/{id}")
    @Operation(summary = "Deletes a model")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "model deleted"),
            @APIResponse(responseCode = "404", description = "model not found")
    })
    @Transactional
    Response delete(@PathParam("id") Long id);

    @DELETE
    @Path("/{id}/select")
    @Operation(summary = "Deletes a model")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "model selected"),
            @APIResponse(responseCode = "404", description = "model not found")
    })
    @Transactional
    Response selectSubModel(@PathParam("id") Long id);

    @GET
    @Path("predictions")
    @Operation(summary = "Lists all user predictions")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all predictions")
    })
    @Transactional
    List<DataAnalysisPredictionDTO> listAllExperiment();

    @GET
    @Path("/{id}/predictions")
    @Operation(summary = "Lists all user predictions for model")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all predictions")
    })
    @Transactional
    List<DataAnalysisPredictionDTO> listPredictions(@PathParam("id") Long id);

    @POST
    @Operation(summary = "Creates a new Prediction")
    @Path("/{id}/sub/{subId}/predictions")
    @APIResponse(responseCode = "201", description = "Prediction created",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = DataAnalysisPredictionDTO.class)
            ))
    @APIResponse(responseCode = "400", description = "Invalid data")
    @Transactional
    Response createPredictions(@PathParam("id") Long id,
                                      @PathParam("subId") Long subId,
                                      @RequestBody @Valid DataAnalysisCreatePredictionDTO createDTO);

}
