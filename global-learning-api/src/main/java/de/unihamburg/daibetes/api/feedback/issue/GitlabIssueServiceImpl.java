package de.unihamburg.daibetes.api.feedback.issue;

import de.unihamburg.daibetes.config.FLNetConfig;
import de.unihamburg.daibetes.services.GitlabIssueClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class GitlabIssueServiceImpl implements GitlabIssueService{

    @Inject
    @RestClient
    GitlabIssueClient gitlabIssueClient;

    @Inject
    FLNetConfig config;

    @Override
    public Response create(GitlabCreateIssueDTO dto) {
        int gitLabIssueProjectId = config.gitlab().issueProjectId();
        try (Response gitlabResponse = gitlabIssueClient.createIssue(gitLabIssueProjectId, dto)) {
            if (gitlabResponse.getStatus() >= 200 && gitlabResponse.getStatus() < 300) {
                return Response.ok().build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("GitLab Issue creation failed: " + gitlabResponse.getStatus())
                        .build();
            }
        }
    }
}
