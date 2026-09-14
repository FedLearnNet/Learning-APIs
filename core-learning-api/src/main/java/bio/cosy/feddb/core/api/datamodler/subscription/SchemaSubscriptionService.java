package bio.cosy.feddb.core.api.datamodler.subscription;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDetailDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

@Path("/schema/subscriptions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "SchemaSubscription", description = "Service for schema subscribe/unsubscribe operations")
public interface SchemaSubscriptionService {


    @GET
    @Path("/{id}")
    @Operation(
            summary = "Subscribe a schema",
            description = "Subscribe a single schema for the current user and return the schema."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Schema subscribed successfully",
                    content = @Content(schema = @Schema(implementation = SchemaNodeDetailDTO.class))
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Schema not found or user not found"
            ),
            @APIResponse(
                    responseCode = "500",
                    description = "Unexpected error"
            )
    })
        // @RolesAllowed({"schema-subscribe"})
    Uni<SchemaStructureDTO> subscribe(
            @Parameter(description = "Schema ID", required = true)
            @PathParam("id") UUID id
    );


    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Unsubscribe a schema",
            description = "Unsubscribe the current user from the given schema."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Schema unsubscribed successfully"
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Schema not found"
            )
    })
        // @RolesAllowed({"schema-subscribe"})
    Uni<Response> unsubscribe(
            @Parameter(description = "Schema ID", required = true)
            @PathParam("id") UUID id
    );
}
