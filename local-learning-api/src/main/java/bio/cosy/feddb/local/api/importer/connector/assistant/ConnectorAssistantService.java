package bio.cosy.feddb.local.api.importer.connector.assistant;

import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/connectors/transformer-assistant")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Connector", description = "Service for managing connectors")
public interface ConnectorAssistantService {

    @POST
    @ResponseStatus(201)
    @Operation(summary = "Start the assistant that suggests transformers for the connector in the background")
    ConnectorAssistantRunDTO start(ConnectorDTO connector);

    @GET
    @Path("/{runId}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Operation(summary = "Events of the assistant run",
            description = "Streams the events so far first, then every new one. The run ends when the stream is closed")
    Multi<TransformerAssistantMessageDTO> events(@PathParam("runId") String runId);

    @POST
    @Path("/{runId}/messages")
    @Operation(summary = "Answer, instruct, stop or apply the assistant run")
    void message(@PathParam("runId") String runId, TransformerAssistantMessageDTO message);
}
