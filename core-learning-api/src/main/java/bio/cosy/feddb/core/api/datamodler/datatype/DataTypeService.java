package bio.cosy.feddb.core.api.datamodler.datatype;

import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import bio.cosy.feddb.core.api.datamodler.validation.ValidationResultDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import io.smallrye.mutiny.Uni;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.Separator;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.jboss.resteasy.reactive.RestResponse.StatusCode.CREATED;

@Path("/datatype")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "DataType", description = "Service for DataType operations")
public interface DataTypeService {


    @GET
    @Operation(summary = "List all datatypes", description = "Lists all datatypes, optionally filtered by ontologyId or search string.")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "List of datatypes",
                    content = @Content(schema = @Schema(implementation = DataTypeNodeDTO.class, type = SchemaType.ARRAY))
            )
    })
        // @RolesAllowed({"datatype-read", "datatype-write"})
    Uni<PagedResponse<DataTypeNodeDTO>> list(
            @Parameter(
                    name = "ontologyId",
                    description = "Ontology ID for filtering. If a datatype is linked to the given ontology, it will be included in the result."
            )
            @QueryParam("ontologyId") UUID ontologyId,
            @Parameter(
                    name = "page",
                    description = "Page number (1-based)"
            )
            @DefaultValue("0") @QueryParam("page") int page,
            @Parameter(
                    name = "page_size",
                    description = "Page size"
            )
            @DefaultValue("200") @QueryParam("page_size") int pageSize,
            @Parameter(
                    name = "search",
                    description = "Search string to filter. A datatype is included if it's name, description, allowed_values, validations or type contains the search string (case insensitive)."
            )
            @QueryParam("search") String search
    );

    @GET
    @Path("/detailed")
    @Operation(
            summary = "List all datatypes with details",
            description = "Lists all datatypes connected to a given schema node (schemaId) or by a list of dataTypeIds."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "List of detailed datatypes",
                    content = @Content(schema = @Schema(implementation = DataTypeNodeDetailDTO.class, type = SchemaType.ARRAY))
            )
    })
    Uni<PagedResponse<DataTypeNodeDetailDTO>> listDetailed(
            @Parameter(
                    name = "schemaId",
                    description = "Schema ID for filter"
            )
            @QueryParam("schemaId") UUID schemaId,
            @Parameter(
                    name = "dataTypeIds",
                    description = "Comma separated list of DataType IDs",
                    schema = @Schema(type = SchemaType.ARRAY, implementation = String.class)
            )
            @Separator(",")
            @QueryParam("dataTypeIds") List<UUID> dataTypeIds,
            @Parameter(
                    name = "page",
                    description = "Page number (1-based)"
            )
            @DefaultValue("0") @QueryParam("page") int page,
            @Parameter(
                    name = "page_size",
                    description = "Page size"
            )
            @DefaultValue("200") @QueryParam("page_size") int pageSize
    );

    @GET
    @Path("/query")
    @Operation(
            summary = "List datatypes for ontology query",
            description = "Lists all datatypes for the given ontology IDs."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "List of datatype subscriptions",
                    content = @Content(schema = @Schema(implementation = DataTypeSubscriptionDTO.class, type = SchemaType.ARRAY))
            )
    })
    Uni<List<DataTypeSubscriptionDTO>> listForQuery(
            @Parameter(
                    name = "ontology-ids",
                    description = "Comma separated ontology IDs for filter (e.g. in query)"
            )
            @Separator(",")
            @QueryParam("ontology-ids") List<UUID> ontologyIds
    );

    @POST
    @Path("/dummy-data")
    @Operation(
            summary = "Generate dummy data",
            description = """
                    Generates dummy data for the given ontology datatype combinations.
                    Does NOT check whether the combinations are valid/exist in the database.
                    If 'asFile' is true, returns a CSV file as attachment instead of JSON.
                    """
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Dummy data generated",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(
                                            implementation = Map.class,
                                            type = SchemaType.ARRAY
                                    )
                            ),
                            @Content(
                                    mediaType = "text/csv",
                                    schema = @Schema(
                                            type = SchemaType.STRING,
                                            description = "CSV export of the dummy data"
                                    )
                            )
                    }
            ),
            @APIResponse(
                    responseCode = "400",
                    description = "Bad Request"
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Datatype not found or no data generated"
            )
    })
    Uni<Response> generateDummyData(@Valid DummyDataRequestDTO request);

    @POST
    @Operation(summary = "Create a new DataType", description = "Creates a new DataType.")
    @APIResponses({
            @APIResponse(
                    responseCode = "201",
                    description = "DataType created",
                    content = @Content(schema = @Schema(implementation = DataTypeNodeDTO.class))
            ),
            @APIResponse(
                    responseCode = "400",
                    description = "Invalid data"
            ),
    })
    @ResponseStatus(CREATED)
        // @RolesAllowed({"datatype-create"})
    Uni<DataTypeNodeDTO> create(
            @Parameter(
                    description = "Payload for creating a datatype"
            )
            DataTypeNodeDTO dto
    );

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a DataType by ID", description = "Retrieves a single DataType.")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "DataType found",
                    content = @Content(schema = @Schema(implementation = DataTypeNodeDTO.class))
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "DataType not found"
            )
    })
    Uni<DataTypeNodeDTO> getById(
            @Parameter(description = "DataType ID", required = true)
            @PathParam("id") UUID id
    );

    @GET
    @Path("/ids")
    @Operation(summary = "Get an DataType by IDs", description = "Retrieves a list of datatypes.")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "DataType found"
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "DataType not found"
            )
    })
    Uni<List<DataTypeNodeDTO>> getByIds(
            @Parameter(description = "DataType IDs", required = true)
            @QueryParam("ids") List<UUID> ids
    );

    @GET
    @Path("/{id}/check-validations")
    @Operation(
            summary = "Check DataType validations",
            description = "Checks the validations of a datatype for a given value."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Value valid",
                    content = @Content(schema = @Schema(implementation = ValidationResultDTO.class))
            ),
            @APIResponse(
                    responseCode = "400",
                    description = "Value not valid",
                    content = @Content(schema = @Schema(implementation = ValidationResultDTO.class))
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "DataType not found / Function not found"
            )
    })
    Uni<ValidationResultDTO> checkValidations(
            @Parameter(description = "DataType ID", required = true)
            @PathParam("id") UUID id,
            @Parameter(
                    name = "value",
                    description = "Value to check",
                    required = true
            )
            @QueryParam("value") String value
    );

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a DataType", description = "Updates an existing DataType (full update).")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "DataType updated",
                    content = @Content(schema = @Schema(implementation = DataTypeNodeDTO.class))
            ),
            @APIResponse(
                    responseCode = "400",
                    description = "Invalid data"
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "DataType not found"
            )
    })
    Uni<DataTypeNodeDTO> update(
            @Parameter(description = "DataType ID", required = true)
            @PathParam("id") UUID id,
            @Parameter(description = "Updated DataType payload", required = true)
            DataTypeNodeDTO dto
    );


    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a DataType", description = "Deletes a DataType.")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "DataType deleted successfully"
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "DataType not found"
            )
    })
    Uni<Response> delete(
            @Parameter(description = "DataType ID", required = true)
            @PathParam("id") UUID id
    );
}
