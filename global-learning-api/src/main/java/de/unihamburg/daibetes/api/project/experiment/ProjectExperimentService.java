package de.unihamburg.daibetes.api.project.experiment;

import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.CreateProjectFederatedExperimentDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentDetailDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepDTO;
import de.unihamburg.daibetes.api.project.experiment.local.CreateProjectLocalExperimentDTO;
import de.unihamburg.daibetes.api.project.experiment.local.ProjectLocalExperimentDTO;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepDetailDTO;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;


@Path("/project/{id}/experiment")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "Project", description = "Service for managing project operations")
public interface ProjectExperimentService {

    @GET
    @Path("federated")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Lists all federated experiments.")
    @APIResponse(responseCode = "200", description = "List of federated experiments")
    @Transactional
    List<ProjectFederatedExperimentDTO> listFederated(@PathParam("id") Long id);

    @POST
    @Path("federated")
    @Operation(summary = "Creates a new Project experiment.")
    @APIResponse(responseCode = "201", description = "Project experiment created",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ProjectFederatedExperimentDTO.class)
            ))
    @APIResponse(responseCode = "400", description = "Invalid data")
    @Transactional
    Response createFederated(@PathParam("id") Long id, @RequestBody @Valid CreateProjectFederatedExperimentDTO createDTO);

    @GET
    @Path("federated/{eId}")
    @Operation(summary = "Retrieves a single project experiment.")
    @APIResponse(responseCode = "200", description = "Project experiment found")
    @APIResponse(responseCode = "404", description = "Project experiment not found")
    @Transactional
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<ProjectFederatedExperimentDetailDTO> retrieveFederated(@PathParam("id") Long id, @PathParam("eId") Long eId);

    @PUT
    @Path("federated/{eId}/start")
    @Operation(summary = "Starts the federated learning process.")
    @APIResponse(responseCode = "200", description = "Project experiment found")
    @APIResponse(responseCode = "404", description = "Project experiment not found")
    @Transactional
    ProjectFederatedExperimentDetailDTO startFederatedLearning(@PathParam("id") Long id, @PathParam("eId") Long eId);


    @PUT
    @Path("federated/{eId}/stop")
    @Operation(summary = "Stops the federated learning process.")
    @APIResponse(responseCode = "200", description = "Project experiment found")
    @APIResponse(responseCode = "404", description = "Project experiment not found")
    @Transactional
    ProjectFederatedExperimentDetailDTO stopFederatedLearning(@PathParam("id") Long id, @PathParam("eId") Long eId);

    @GET
    @Path("federated/{experimentId}/step/{stepId}")
    @Operation(summary = "Stream Log messages of a federated experiment step.")
    @APIResponse(responseCode = "200", description = "Project experiment found")
    @APIResponse(responseCode = "404", description = "Project experiment not found")
    @Transactional
    ProjectFederatedExperimentStepDTO getFederatedStepDetailUpdates(@PathParam("id") Long id,
                                                                    @PathParam("experimentId") Long experimentId,
                                                                    @PathParam("stepId") Long stepId
    );

    @GET
    @Path("local")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Lists all federated experiments.")
    @APIResponse(responseCode = "200", description = "List of federated experiments")
    @Transactional
    List<ProjectLocalExperimentDTO> listLocal(@PathParam("id") Long id);

    @POST
    @Path("local")
    @Operation(summary = "Creates a new Project experiment.")
    @APIResponse(responseCode = "201", description = "Project experiment created",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ProjectLocalExperimentDTO.class)
            ))
    @APIResponse(responseCode = "400", description = "Invalid data")
    @Transactional
    Response createLocal(@PathParam("id") Long id, @RequestBody @Valid CreateProjectLocalExperimentDTO createDTO);

    @GET
    @Path("local/{lId}")
    @Operation(summary = "Retrieves a single project experiment.")
    @APIResponse(responseCode = "200", description = "Project experiment found")
    @APIResponse(responseCode = "404", description = "Project experiment not found")
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<ProjectLocalExperimentDTO> retrieveLocal(@PathParam("id") Long id, @PathParam("lId") Long eId);

    @PUT
    @Path("local/{eId}/start")
    @Operation(summary = "start a local experiment.")
    @APIResponse(responseCode = "200", description = "Project experiment found")
    @APIResponse(responseCode = "404", description = "Project experiment not found")
    @Transactional
    ProjectLocalExperimentDTO startLocal(@PathParam("id") Long id, @PathParam("eId") Long eId);


    @PUT
    @Path("local/{eId}/stop")
    @Operation(summary = "stop a local experiment.")
    @APIResponse(responseCode = "200", description = "Project experiment found")
    @APIResponse(responseCode = "404", description = "Project experiment not found")
    @Transactional
    ProjectLocalExperimentDTO stopLocal(@PathParam("id") Long id, @PathParam("eId") Long eId);


    @POST
    @Path("local/test")
    @Operation(summary = "Creates a new Project experiment.")
    @APIResponse(responseCode = "201", description = "Project experiment created",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ProjectLocalExperimentDTO.class)
            ))
    @APIResponse(responseCode = "400", description = "Invalid data")
    @Transactional
    Response createLocalTest(@PathParam("id") Long id);

    @GET
    @Path("local/test")
    @Operation(summary = "Retrieves a single project experiment.")
    @APIResponse(responseCode = "200", description = "Project experiment found")
    @APIResponse(responseCode = "404", description = "Project experiment not found")
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<ProjectLocalExperimentDTO> retrieveLocalTest(@PathParam("id") Long id);


    @GET
    @Path("local/{experimentId}/step/{stepId}/messages")
    @Operation(summary = "Stream Log messages")
    @APIResponse(responseCode = "200", description = "Project experiment found")
    @APIResponse(responseCode = "404", description = "Project experiment not found")
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<RunMessageLogDTO> listLocalLogMessages(@PathParam("id") Long id,
                                                 @PathParam("experimentId") Long experimentId,
                                                 @PathParam("stepId") Long stepId);

    @GET
    @Path("local/{experimentId}/step/{stepId}")
    @Operation(summary = "Stream Log messages")
    @APIResponse(responseCode = "200", description = "Project experiment found")
    @APIResponse(responseCode = "404", description = "Project experiment not found")
    @Transactional
    ProjectLocalExperimentStepDetailDTO getLocalStepDetailUpdates(@PathParam("id") Long id,
                                                                  @PathParam("experimentId") Long experimentId,
                                                                  @PathParam("stepId") Long stepId
    );


}
