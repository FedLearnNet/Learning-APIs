package bio.cosy.feddb.local.api.issue;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/issue")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Issue Service", description = "Service for gitlab issues")
public interface GitlabIssueService {

    @POST
    @Operation(summary = "Create a new GitLab issue", description = "Creates a new issue in GitLab with the given title and description")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Issue created successfully"),
            @APIResponse(
                    responseCode = "400",
                    description = "GitLab issue creation failed",
                    content = @Content(schema = @Schema(implementation = String.class))
            )
    })
    Response create(GitlabCreateIssueDTO dto);
}
