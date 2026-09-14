package bio.cosy.feddb.local.api.cohort.queryability;

import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/cohort/{cohortId}/queryability")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "Cohort", description = "Service for managing CohortQueryabilities")
public interface CohortQueryAbilityService {

    @GET
    @Operation(summary = "List all CohortQueryabilities", description = "Returns a list of all CohortQueryability records of the cohort")
    @APIResponse(responseCode = "200", description = "List of all CohortQueryabilities")
    @Transactional
    List<CohortQueryAbilityDTO> list(@PathParam("cohortId") Long cohortId);


    @GET
    @Path("/{id}")
    @Operation(summary = "Retrieve a single CohortQueryability", description = "Returns a single CohortQueryability by its Id")
    @APIResponse(responseCode = "200", description = "CohortQueryability found")
    @APIResponse(responseCode = "404", description = "CohortQueryability not found")
    @Transactional
    CohortQueryAbilityDTO retrieve(@PathParam("cohortId") Long cohortId, @PathParam("id") Long id);

    @POST
    @Operation(summary = "Create a new CohortQueryability", description = "Creates a new CohortQueryability record")
    @APIResponse(responseCode = "201", description = "CohortQueryability  created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = CohortQueryAbilityDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "CohortQueryability  not found")
    @Transactional
    Response create(@PathParam("cohortId") Long cohortId, @Valid CreateCohortQueryAbilityDTO createDTO);

    @POST
    @Path("/bulk")
    @Operation(summary = "Create multiple new CohortQueryabilities", description = "Creates a new CohortQueryability list")
    @APIResponse(responseCode = "201", description = "CohortQueryability bulk created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = CohortQueryAbilityDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "CohortQueryability not found")
    @Transactional
    Response createBulk(@PathParam("cohortId") Long cohortId, List<@Valid CreateCohortQueryAbilityDTO> createDTO);


    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a CohortQueryability", description = "Updates an existing CohortQueryability record")
    @APIResponse(responseCode = "200", description = "CohortQueryability updated")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "CohortQueryability not found")
    @Transactional
    CohortQueryAbilityDTO update(
            @PathParam("cohortId") Long cohortId,
            @PathParam("id") Long id,
            @Valid CohortQueryAbilityDTO updateDTO);


    @PUT
    @Path("/bulk")
    @Operation(summary = "Update multiple CohortQueryabilities", description = "Updates multiple CohortQueryability records")
    @APIResponse(responseCode = "200", description = "CohortQueryabilities updated")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "CohortQueryability or CohortID not found")
    @Transactional
    List<CohortQueryAbilityDTO> updateBulk(
            @PathParam("cohortId") Long cohordId,
            List<@Valid CreateCohortQueryAbilityDTO> createDTO);

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a CohortQueryability", description = "Deletes a CohortQueryability record by its id")
    @APIResponse(responseCode = "200", description = "CohortQueryability deleted")
    @Transactional
    Response delete(@PathParam("cohortId") Long cohordId, @PathParam("id") Long id);
}
