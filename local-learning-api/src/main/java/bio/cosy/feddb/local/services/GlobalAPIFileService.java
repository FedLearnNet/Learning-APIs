package bio.cosy.feddb.local.services;

import bio.cosy.feddb.core.api.file.FileDTO;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;

// The platform's file store, under the client's account.
@RegisterRestClient(configKey = "global-api")
@RegisterProvider(GlobalAPIAuthRequestFilter.class)
@Path("/files")
public interface GlobalAPIFileService {

    @POST
    @Path("upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    FileDTO upload(ClientMultipartForm form);

    @DELETE
    @Path("{fileId}")
    Response delete(@PathParam("fileId") Long fileId, @QueryParam("secret") String secret);
}
