package bio.cosy.feddb.local.api.cohort;

import bio.cosy.feddb.local.api.cohort.member.CohortAvailableUserDTO;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberCreateDTO;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberDTO;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path(CohortService.PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "Cohort", description = "Service for managing Cohorts")
public interface CohortService {
    public static final String PATH = "/cohort";

    @GET
    @Operation(summary = "List all cohorts", description = "Returns a list of all cohort records")
    @APIResponse(responseCode = "200", description = "List of all cohorts")
    @Transactional
    List<CohortDTO> list();

    @POST
    @Operation(summary = "Create a new cohort", description = "Creates a new cohort record")
    @APIResponse(responseCode = "201", description = "cohort created")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "409", description = "Cohort name already exists")
    @APIResponse(responseCode = "404", description = "Cohort not found")
    @Transactional
    CohortDTO create(@Valid CreateCohortDTO createDTO);

    @GET
    @Path("/health")
    @Operation(
            summary = "Check cohort name availability",
            description = "Returns whether a cohort name is already taken. Pass excludeId when editing to ignore the current cohort."
    )
    @APIResponse(responseCode = "200", description = "Name health check result")
    @APIResponse(responseCode = "400", description = "Name missing or blank")
    @APIResponse(responseCode = "404", description = "Cohort not found (when excludeId is provided)")
    @Transactional
    CohortNameHealthDTO checkNameHealth(
            @QueryParam("name")
            @Parameter(description = "Cohort name to check", in = ParameterIn.QUERY, required = true)
            String name,
            @QueryParam("excludeId")
            @Parameter(description = "Cohort ID to exclude from the check (edit mode)", in = ParameterIn.QUERY)
            Long excludeId
    );

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a cohort by ID", description = "Returns a cohort record by its internal ID")
    @APIResponse(responseCode = "200", description = "Cohort found")
    @APIResponse(responseCode = "404", description = "Cohort not found")
    @Transactional
    CohortDetailDTO get(@PathParam("id") Long id);

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a cohort", description = "Updates an existing cohort record by its internal ID")
    @APIResponse(responseCode = "200", description = "Cohort updated")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "409", description = "Cohort name already exists")
    @APIResponse(responseCode = "404", description = "Cohort not found")
    @Transactional
    CohortDetailDTO update(@PathParam("id") Long id, @Valid UpdateCohortDTO updateDTO);

    @POST
    @Path("/{id}/members")
    @Operation(summary = "Add a cohort member", description = "Adds a new member to an existing cohort")
    @APIResponse(responseCode = "201", description = "Cohort member created")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "Cohort not found")
    @Transactional
    CohortMemberDTO addMember(@PathParam("id") Long id, @Valid CohortMemberCreateDTO createDTO);


    @POST
    @Path("members")
    @Operation(summary = "Add a cohort member", description = "Get all avaible local users")
    @APIResponse(responseCode = "200", description = "All users")
    @Transactional
    List<CohortAvailableUserDTO> getAllUsers();


    @PUT
    @Path("/{cohortId}/members/{id}")
    @Operation(summary = "Update a cohort member", description = "Updates an existing cohort member")
    @APIResponse(responseCode = "200", description = "Cohort member updated")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "Cohort member not found")
    @Transactional
    CohortMemberDTO updateMember(@PathParam("cohortId") Long cohortId, @PathParam("id") Long id, @Valid CohortMemberDTO updateDTO);

    @DELETE
    @Path("/{cohortId}/members/{id}")
    @Operation(summary = "Delete a cohort member", description = "Deletes a cohort member from an existing cohort")
    @APIResponse(responseCode = "200", description = "Cohort member deleted")
    @APIResponse(responseCode = "404", description = "Cohort member not found")
    @Transactional
    Response deleteMember(@PathParam("cohortId") Long cohortId, @PathParam("id") Long id);

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a cohort", description = "Starts background deletion and returns immediately.")
    @APIResponse(responseCode = "202", description = "Cohort deletion started in background")
    @APIResponse(responseCode = "409", description = "Deletion already in progress or imports are running")
    Response delete(@PathParam("id") Long id);
}
