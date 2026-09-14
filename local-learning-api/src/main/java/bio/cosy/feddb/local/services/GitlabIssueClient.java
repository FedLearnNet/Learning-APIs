package bio.cosy.feddb.local.services;

import bio.cosy.feddb.local.api.issue.GitlabCreateIssueDTO;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "global-api")
@RegisterProvider(GlobalAPIAuthRequestFilter.class)
@Path("/issue")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface GitlabIssueClient {

    @POST
    Response createIssue( GitlabCreateIssueDTO request);
}
