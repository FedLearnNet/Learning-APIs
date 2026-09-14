package bio.cosy.feddb.local.api.cohort.statistics.dashboard;

import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path(CustomStatisticsDashboardService.PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "CustomStatisticsDashboard",
        description = "Manage user-named tabs/dashboards that group custom statistics for a cohort")
public interface CustomStatisticsDashboardService {
    String PATH = "/customstatisticsdashboard";

    @GET
    @Path("/cohort/{cohortId}")
    @Operation(summary = "List dashboards for a cohort",
            description = "Returns the user-created custom statistics dashboards belonging to the given cohort")
    @APIResponse(responseCode = "200", description = "Dashboards listed")
    @APIResponse(responseCode = "404", description = "Cohort not found")
    @Transactional
    List<CustomStatisticsDashboardDTO> listForCohort(@PathParam("cohortId") Long cohortId);

    @POST
    @Operation(summary = "Create a dashboard", description = "Creates a new custom statistics dashboard for a cohort")
    @APIResponse(responseCode = "201", description = "Dashboard created")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "Cohort not found")
    @Transactional
    CustomStatisticsDashboardDTO create(@Valid CreateCustomStatisticsDashboardDTO createDTO);

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a dashboard", description = "Updates an existing custom statistics dashboard")
    @APIResponse(responseCode = "200", description = "Dashboard updated")
    @APIResponse(responseCode = "404", description = "Dashboard not found")
    @Transactional
    CustomStatisticsDashboardDTO update(@PathParam("id") Long id, @Valid CustomStatisticsDashboardDTO updateDTO);

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a dashboard",
            description = "Deletes a dashboard and all of its custom statistics")
    @APIResponse(responseCode = "200", description = "Dashboard deleted")
    @APIResponse(responseCode = "404", description = "Dashboard not found")
    @Transactional
    Response delete(@PathParam("id") Long id);
}
