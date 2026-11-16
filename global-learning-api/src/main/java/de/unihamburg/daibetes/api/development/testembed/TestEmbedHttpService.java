package de.unihamburg.daibetes.api.development.testembed;

import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import de.unihamburg.daibetes.api.development.engine.TestEngineRunBO;
import de.unihamburg.daibetes.api.development.run.TestRunBO;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/testembed/{appId}")
@Authenticated
public class TestEmbedHttpService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    TestRunBO testRunBO;

    @Inject
    TestEngineRunBO engineBO;

    @POST
    @Path("credential")
    @Produces(MediaType.APPLICATION_JSON)
    public Response bootstrap(@PathParam("appId") Long appId) {
        return Response.ok(Map.of("apiKey", engineBO.bootstrapCredentialTransactional(appId, userIdentity.getKeycloakId())))
                .header("Cache-Control", "no-store").build();
    }

    @GET
    @Path("upload/output/{runId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadOutput(@PathParam("appId") Long appId, @PathParam("runId") Long runId,
                                   @QueryParam("runType") AppRunTypeEnum runType) {
        return Response.ok(testRunBO.downloadOutputTransactional(appId, runId, runType, userIdentity.getKeycloakId()))
                .header("Content-Disposition", "attachment; filename=download.zip")
                .header("Content-Type", "application/zip").build();
    }
}
