package bio.cosy.feddb.core.services.controller;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@Path("/start-learning")
@Produces("application/json")
@Consumes("application/json")
public interface ControllerLearningService {

    @POST
    Response startLearning(ControllerStartLearningRequestDTO request);
}
