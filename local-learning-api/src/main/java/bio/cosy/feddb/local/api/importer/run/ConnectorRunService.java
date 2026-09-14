package bio.cosy.feddb.local.api.importer.run;


import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunMessagesDTO;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/connectors/runs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Run", description = "Service for managing runs")
@Authenticated
public interface ConnectorRunService {

    @GET
    @Operation(summary = "List all runs")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "List of runs"
            )
    })
    List<ConnectorRunDTO> listRuns();

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a run by id")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Run found",
                    content = @Content(schema = @Schema(implementation = ConnectorRunDTO.class))
            ),
            @APIResponse(responseCode = "404", description = "Run not found")
    })
    ConnectorRunDTO getRun(
            @PathParam("id")
            @Parameter(description = "Run ID", in = ParameterIn.PATH, required = true)
            Long id
    );

    @GET
    @Path("/connectors/{connectorId}")
    @Operation(summary = "Get all runs for a connector")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Runs for connector"
            ),
            @APIResponse(responseCode = "404", description = "Connector not found")
    })
    List<ConnectorRunDTO> getAllRunsForConnector(
            @PathParam("connectorId")
            @Parameter(description = "Connector ID", in = ParameterIn.PATH, required = true)
            Long connectorId
    );

    @GET
    @Path("/{id}/logs")
    @Operation(summary = "Get all logs for a run")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Run logs"),
            @APIResponse(responseCode = "404", description = "Run not found")
    })
    List<ConnectorRunMessagesDTO> getLogs(
            @PathParam("id")
            @Parameter(description = "Run ID", in = ParameterIn.PATH, required = true)
            Long id
    );

    @GET
    @Path("/{id}/run-logs")
    @Operation(summary = "Get all run error logs for a run")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Run error logs",
                    content = @Content(schema = @Schema(implementation = RunErrorLogListResponseDTO.class))
            ),
            @APIResponse(responseCode = "404", description = "Run not found")
    })
    RunErrorLogListResponseDTO getRunLogs(
            @PathParam("id")
            @Parameter(description = "Run ID", in = ParameterIn.PATH, required = true)
            Long id,
            @QueryParam("type")
            @Parameter(
                    description = "Log type: Loading, Transform, Mapping, Persistence",
                    in = ParameterIn.QUERY
            )
            String type,
            @QueryParam("patient")
            @Parameter(
                    description = "Patient-related logs filter: yes, no, or unset",
                    in = ParameterIn.QUERY
            )
            String patient
    );
}
