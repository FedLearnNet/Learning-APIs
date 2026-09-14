package bio.cosy.feddb.local.api.cohort.statistics.entry;

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

@Path(CustomStatisticService.PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "CustomStatistic",
        description = "Manage user-defined custom statistics inside a dashboard")
public interface CustomStatisticService {
    String PATH = "/customstatistics";

    @GET
    @Path("/dashboard/{dashboardId}")
    @Operation(summary = "List statistics for a dashboard",
            description = "Returns the custom statistics belonging to the given dashboard")
    @APIResponse(responseCode = "200", description = "Statistics listed")
    @APIResponse(responseCode = "404", description = "Dashboard not found")
    @Transactional
    List<CustomStatisticDTO> listForDashboard(@PathParam("dashboardId") Long dashboardId);

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a custom statistic", description = "Returns a single custom statistic by id")
    @APIResponse(responseCode = "200", description = "Statistic found")
    @APIResponse(responseCode = "404", description = "Statistic not found")
    @Transactional
    CustomStatisticDTO get(@PathParam("id") Long id);

    @POST
    @Operation(summary = "Create a custom statistic",
            description = "Creates a new custom statistic inside a dashboard")
    @APIResponse(responseCode = "201", description = "Statistic created")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "Dashboard not found")
    @Transactional
    CustomStatisticDTO create(@Valid CreateCustomStatisticDTO createDTO);

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a custom statistic",
            description = "Updates an existing custom statistic")
    @APIResponse(responseCode = "200", description = "Statistic updated")
    @APIResponse(responseCode = "404", description = "Statistic not found")
    @Transactional
    CustomStatisticDTO update(@PathParam("id") Long id, @Valid CustomStatisticDTO updateDTO);

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a custom statistic", description = "Deletes a custom statistic by id")
    @APIResponse(responseCode = "200", description = "Statistic deleted")
    @APIResponse(responseCode = "404", description = "Statistic not found")
    @Transactional
    Response delete(@PathParam("id") Long id);
}
