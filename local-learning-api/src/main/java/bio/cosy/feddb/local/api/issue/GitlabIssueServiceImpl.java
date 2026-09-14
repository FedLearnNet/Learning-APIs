package bio.cosy.feddb.local.api.issue;

import bio.cosy.feddb.local.services.GitlabIssueClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class GitlabIssueServiceImpl implements GitlabIssueService {

    @Inject
    @RestClient
    GitlabIssueClient gitlabIssueClient;

    @Override
    public Response create(GitlabCreateIssueDTO dto) {
        try (Response gitlabResponse = gitlabIssueClient.createIssue(dto)) {
            if (gitlabResponse.getStatus() >= 200 && gitlabResponse.getStatus() < 300) {
                return Response.ok().build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("GitLab Issue creation failed: " + gitlabResponse.getStatus()).build();
            }
        }
    }
}
