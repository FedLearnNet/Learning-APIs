package bio.cosy.feddb.local.services;


import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;

@RegisterRestClient(configKey = "global-api")
@RegisterProvider(GlobalAPIAuthRequestFilter.class)
@Path("/model/result")
public interface GlobalAPIModelResultService {

    @POST
    @Path("/experiment/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    Response upload(ClientMultipartForm form);
}
