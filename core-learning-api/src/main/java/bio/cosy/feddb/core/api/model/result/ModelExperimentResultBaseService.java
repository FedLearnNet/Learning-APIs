package bio.cosy.feddb.core.api.model.result;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;


@Path("/model/result")
@Produces("application/json")
@Consumes("application/json")
//@RolesAllowed("admin")
//@Authenticated TODO LATER
public interface ModelExperimentResultBaseService {

    @POST
    @Path("/experiment/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    Response upload(@BeanParam ModelExperimentResultDTO req);
}
