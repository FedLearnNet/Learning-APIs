package de.unihamburg.daibetes.api.project.experiment.federated.aggregator;

import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;

/**
 * Lets the platform's own aggregator container upload the model it produced while running a
 * federated experiment's aggregation role (see {@link ProjectFederatedExperimentAggregatorWebsocketService}).
 * A clinic uploads its model via local-learning-api's own {@code /learning/run/{runId}/upload/model}
 * and, if it is the coordinator, local-learning-api relays it on to global-learning-api; the
 * platform aggregator has no local-learning-api of its own and already runs inside global-learning-api,
 * so it uploads directly here instead.
 */
@Path("/project/experiment/federated/aggregator")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "ProjectFederatedExperimentAggregator", description = "Service for the platform's own federated aggregator container")
public interface ProjectFederatedExperimentAggregatorModelService {

    /**
     * Uploads the model the platform aggregator produced for the given step.
     *
     * @param stepId the platform aggregator's step id
     * @param file   the uploaded model file and its metadata
     * @return a 201 response on success
     */
    @POST
    @Path("{stepId}/upload/model")
    @ToolAuthenticated(Scope.FEDERATED_AGGREGATOR_RUN)
    @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
    @Operation(summary = "Upload the platform aggregator's model")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "created"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @ResponseStatus(201)
    Response uploadModel(@PathParam("stepId") Long stepId, @BeanParam ModelSubDataDTO file);
}
