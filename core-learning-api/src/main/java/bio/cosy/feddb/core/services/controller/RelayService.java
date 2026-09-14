package bio.cosy.feddb.core.services.controller;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;


@Produces("application/json")
@Consumes("application/json")
public interface RelayService {

    @POST
    @Path("{id}/setup")
    @Deprecated
    Response setup(@PathParam("id") String channelId);

    @POST
    @Path("create-fl-run")
    CreateFLLearningRelayServerResponseDTO setupFL(CreateFLLearningRelayServerRequestDTO setup);
}
