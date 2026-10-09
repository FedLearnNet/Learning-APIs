package bio.cosy.feddb.core.services.controller;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

/**
 * REST API of the controller for the runs of federated workflow steps.
 */
@Path("/")
@Produces("application/json")
@Consumes("application/json")
public interface ControllerLearningService {

    @POST
    @Path("start-learning")
    Response startLearning(ControllerStartLearningRequestDTO request);

    /**
     * Passes the signed certificate to the controller, which then connects the run to the relay server.
     */
    @POST
    @Path("start-relaying")
    Response startRelaying(ControllerStartRelayingRequestDTO request);

    /**
     * Stops the run on the controller.
     */
    @POST
    @Path("stop-learning")
    Response stopLearning(ControllerStopLearningRequestDTO request);
}
