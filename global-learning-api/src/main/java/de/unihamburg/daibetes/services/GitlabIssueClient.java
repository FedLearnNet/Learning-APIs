package de.unihamburg.daibetes.services;

import de.unihamburg.daibetes.api.feedback.issue.GitlabCreateIssueDTO;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "gitlab-api")
@ClientHeaderParam(name = "PRIVATE-TOKEN", value = "${flnet.gitlab.issue.token}")
public interface GitlabIssueClient {

    @POST
    @Path("/projects/{id}/issues")
    @Consumes(MediaType.APPLICATION_JSON)
    Response createIssue(
            @PathParam("id") int projectId,
            GitlabCreateIssueDTO request
    );
}
