package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.preview.PreviewResponseDTO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBulkRequestDTO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.importer.validation.PreviewValidationRequestDTO;
import bio.cosy.feddb.local.api.importer.validation.PreviewValidationResponseDTO;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;

@Path("/connectors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Connector", description = "Service for managing connectors")
@Authenticated
public interface ConnectorService {

    @GET
    @Operation(summary = "List all connectors or filter by cohort")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "List of connectors"
            )
    })
    List<ConnectorDTO> listConnectors(
            @QueryParam("cohort_id")
            @Parameter(
                    description = "Cohort ID to filter connectors",
                    in = ParameterIn.QUERY
            )
            Long cohortId
    );

    @POST
    @Operation(summary = "Create a new connector")
    @APIResponses({
            @APIResponse(
                    responseCode = "201",
                    description = "Connector created successfully",
                    content = @Content(schema = @Schema(implementation = ConnectorDTO.class))
            ),
            @APIResponse(responseCode = "400", description = "Invalid data"),
            @APIResponse(responseCode = "409", description = "Connector name already exists")
    })
    @ResponseStatus(201)
    ConnectorDTO createConnector(
            @QueryParam("raw")
            @Parameter(
                    description = "If true, import as raw copy: remove ids and adjust name",
                    in = ParameterIn.QUERY
            )
            @DefaultValue("false")
            boolean raw,
            ConnectorDTO connectorDTO
    );

    @POST
    @Path("/import-from-remote-url")
    @Operation(summary = "Import connector definition from a remote URL or local file path")
    @APIResponses({
            @APIResponse(
                    responseCode = "201",
                    description = "Connector imported successfully",
                    content = @Content(schema = @Schema(implementation = ConnectorDTO.class))
            ),
            @APIResponse(responseCode = "404", description = "No connector found to import or error occurred"),
            @APIResponse(responseCode = "400", description = "Invalid payload")
    })
    ConnectorDTO importFromRemoteUrl(
            @QueryParam("remote_url")
            @Parameter(
                    description = "Remote HTTP(S), file:// URL, or local filesystem path",
                    in = ParameterIn.QUERY,
                    required = true
            )
            String remoteUrl,
            @QueryParam("cohort_id")
            @Parameter(
                    description = "Cohort ID to assign to the imported connector",
                    in = ParameterIn.QUERY
            )
            Long cohortId
    );

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a connector by id")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Connector found",
                    content = @Content(schema = @Schema(implementation = ConnectorDTO.class))
            ),
            @APIResponse(responseCode = "404", description = "Connector not found")
    })
    ConnectorDTO getConnector(
            @PathParam("id")
            @Parameter(description = "Connector ID", required = true)
            Long id
    );

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a connector")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Connector updated successfully",
                    content = @Content(schema = @Schema(implementation = ConnectorDTO.class))
            ),
            @APIResponse(responseCode = "400", description = "Invalid data"),
            @APIResponse(responseCode = "404", description = "Connector not found")
    })
    ConnectorDTO updateConnector(
            @PathParam("id")
            @Parameter(description = "Connector ID", required = true)
            Long id,
            ConnectorDTO connectorDTO
    );


    @PATCH
    @Path("/{id}")
    @Operation(summary = "Patch a connector")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Connector updated successfully",
                    content = @Content(schema = @Schema(implementation = ConnectorDTO.class))
            ),
            @APIResponse(responseCode = "400", description = "Invalid data"),
            @APIResponse(responseCode = "404", description = "Connector not found")
    })
    ConnectorDTO patchConnector(
            @PathParam("id")
            @Parameter(description = "Connector ID", required = true)
            Long id,
            ConnectorDTO connectorDTO
    );

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a connector")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Connector deleted successfully"),
            @APIResponse(responseCode = "404", description = "Connector not found")
    })
    Response deleteConnector(
            @PathParam("id")
            @Parameter(description = "Connector ID", required = true)
            Long id
    );


        @POST
        @Path("/{id}/run")
        @Operation(summary = "Run a connector")
        @APIResponses({
                        @APIResponse(responseCode = "201", description = "Run created successfully"),
                        @APIResponse(responseCode = "400", description = "Invalid data"),
                        @APIResponse(responseCode = "404", description = "Connector not found")
        })
        Multi<ConnectorRunDTO> runConnector(
                        @PathParam("id") @Parameter(description = "Connector ID", required = true) Long id,
                        @QueryParam("delete-existing-patients") @DefaultValue("false") @Parameter(description = "Controls whether all existing patients are deleted before import.", in = ParameterIn.QUERY) boolean deleteExistingPatients,
                        @QueryParam("dry") @DefaultValue("false") @Parameter(description = "Dry run mode", in = ParameterIn.QUERY) boolean dry);

    @POST
    @Path("/preview")
    @Operation(summary = "Preview a connector configuration")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Preview generated successfully",
                    content = @Content(schema = @Schema(implementation = PreviewResponseDTO.class))
            ),
            @APIResponse(responseCode = "400", description = "Invalid data"),
            @APIResponse(responseCode = "404", description = "Connector not found")
    })
    PreviewResponseDTO previewConnector(ConnectorConfigDTO connectorConfigDTO);

    @DELETE
    @Path("/{connectorId}/preview/cache")
    @Operation(summary = "Invalidate cached preview stages",
            description = "Drops the cached preview of the given step and of every step after it, "
                    + "because each stage is computed from the one before it. Omit fromStep to drop "
                    + "the whole connector's preview cache. Editing a step normally invalidates "
                    + "itself, so this is for forcing a fresh result - re-running an extractor app, "
                    + "for instance, can produce different data from an unchanged configuration.")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Cached stages dropped"),
            @APIResponse(responseCode = "403", description = "Not allowed to edit this connector")
    })
    void invalidatePreviewCache(@PathParam("connectorId") Long connectorId,
                                @QueryParam("fromStep") Integer fromStep);

    @POST
    @Path("/preview/pivot")
    @Operation(summary = "Preview a pivot table")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Preview pivot table generated successfully",
                    content = @Content(schema = @Schema(implementation = PreviewResponseDTO.class))
            ),
            @APIResponse(responseCode = "400", description = "Invalid data"),
            @APIResponse(responseCode = "404", description = "Connector not found")
    })
    PreviewResponseDTO previewPivotTable(ConnectorConfigDTO connectorConfigDTO);

    @POST
    @Path("/preview/validations")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Validate all values used by the connector preview",
            description = "Streams column placeholders first, followed by validated replacements for each column."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Preview-validation SSE stream",
                    content = @Content(
                            mediaType = MediaType.SERVER_SENT_EVENTS,
                            schema = @Schema(implementation = PreviewValidationResponseDTO.class)
                    )
            ),
            @APIResponse(responseCode = "400", description = "Invalid preview validation request"),
            @APIResponse(responseCode = "404", description = "Input file not found")
    })
    Multi<PreviewValidationResponseDTO> validatePreview(
            @NotNull(message = "Preview validation request is required")
            @Valid
            PreviewValidationRequestDTO request
    );

    @GET
    @Path("/check-validations")
    @Operation(summary = "Check a single schema validation")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Valid"),
            @APIResponse(responseCode = "400", description = "Not valid"),
            @APIResponse(responseCode = "404", description = "Schema or function not found")
    })
    ConnectorValidationResultDTO checkValidations(
            @QueryParam("cohort_id")
            @Parameter(
                    description = "Cohort ID / schema ID to validate against",
                    in = ParameterIn.QUERY,
                    required = true
            )
            Long cohortId,
            @QueryParam("value")
            @Parameter(
                    description = "Value to validate",
                    in = ParameterIn.QUERY,
                    required = true
            )
            String value,
            @QueryParam("schemaId")
            @Parameter(
                    description = "Schema id to validate against",
                    in = ParameterIn.QUERY,
                    required = false
            )
            Long schemaId,
            @QueryParam("mapping")
            @Parameter(
                    description = "Schema mapping path; the configured external ID mapping is always valid",
                    in = ParameterIn.QUERY,
                    required = false
            )
            String mapping
    );

    @PUT
    @Path("/{id}/run/{runId}/rollback")
    @Operation(summary = "Rollback a connector run",
            description = "Reverts all patient data changes made by the specified connector run. " +
                    "If delete_audit is true, audit entries from the run are permanently deleted.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Connector run rolled back successfully"),
            @APIResponse(responseCode = "404", description = "Connector or run not found")
    })
    Response rollbackConnectorRun(
            @PathParam("id")
            @Parameter(description = "Connector ID", required = true)
            Long id,
            @PathParam("runId")
            @Parameter(description = "Run ID to rollback", required = true)
            Long runId,
            @QueryParam("delete_audit")
            @DefaultValue("false")
            @Parameter(description = "If true, delete audit entries at and after the rollback point", in = ParameterIn.QUERY)
            boolean deleteAudit
    );

    @POST
    @Path("check-validations-bulk")
    @Operation(summary = "Check multiple schema validations in bulk")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "All valid"),
            @APIResponse(responseCode = "400", description = "At least one validation failed"),
            @APIResponse(responseCode = "404", description = "Schema or function not found")
    })
    List<ConnectorValidationResultDTO> checkValidationsBulk(
            @QueryParam("cohort_id")
            @Parameter(
                    description = "Cohort ID / schema ID to validate against",
                    in = ParameterIn.QUERY,
                    required = true
            )
            Long cohortId,
            List<ConnectorValidationBulkRequestDTO> items
    );
}
