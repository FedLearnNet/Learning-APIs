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

    /**
     * Signs the CSR of a client of an FL run.
     */
    @POST
    @Path("sign-fl-run-cert")
    RelaySignCertResponseDTO signCert(RelaySignCertRequestDTO request);

    /**
     * Stops an FL run on the relay server.
     */
    @POST
    @Path("stop-fl-run")
    Response stopFL(RelayStopRequestDTO request);
}
