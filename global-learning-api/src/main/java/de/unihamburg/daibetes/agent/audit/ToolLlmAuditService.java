package de.unihamburg.daibetes.agent.audit;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("apps/llm-audit")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "LLM Audit", description = "LLM security and privacy audit of tool versions")
public interface ToolLlmAuditService {

    @POST
    @Path("tool-version/{versionId}")
    @Operation(summary = "Request an LLM audit",
            description = "Queues an audit of source code and scan results. Returns the running or an unchanged "
                    + "finished audit instead of starting a new one.")
    @APIResponse(responseCode = "200", description = "Queued, running or reused audit")
    @APIResponse(responseCode = "403", description = "Version not visible or force without admin role")
    @APIResponse(responseCode = "429", description = "User quota or platform queue exhausted")
    @APIResponse(responseCode = "503", description = "LLM audits are disabled")
    ToolLlmAuditDTO request(@PathParam("versionId") Long versionId,
                            @Parameter(in = ParameterIn.QUERY, description = "Admin only: audit again although nothing changed")
                            @QueryParam("force") @DefaultValue("false") boolean force);

    @GET
    @Path("tool-version/{versionId}")
    @Operation(summary = "List LLM audits of a tool version", description = "Newest first")
    @APIResponse(responseCode = "200", description = "Audits")
    List<ToolLlmAuditDTO> listByVersion(@PathParam("versionId") Long versionId);

    @GET
    @Path("tool-version/{versionId}/latest")
    @Operation(summary = "Latest LLM audit of a tool version")
    @APIResponse(responseCode = "200", description = "Latest audit")
    @APIResponse(responseCode = "204", description = "No audit yet")
    Response findLatestByVersion(@PathParam("versionId") Long versionId);

    @GET
    @Path("{id}")
    @Operation(summary = "Get an LLM audit")
    @APIResponse(responseCode = "200", description = "Audit")
    @APIResponse(responseCode = "404", description = "Not found")
    ToolLlmAuditDTO get(@PathParam("id") Long id);

    @GET
    @Path("quota")
    @Operation(summary = "Remaining LLM audit requests of the current user")
    @APIResponse(responseCode = "200", description = "Quota")
    ToolLlmAuditQuotaDTO quota();
}
