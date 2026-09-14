package bio.cosy.feddb.local.api.importer.run.execution;

import bio.cosy.feddb.core.api.run.AppRunUploadData;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class ConnectorRunExecutionServiceImpl implements ConnectorRunExecutionService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    ConnectorRunExecutionRunBO dataAnalysisRunBO;

    @Override
    public Response uploadOutput(Long runId, AppRunUploadData req) {
        String keycloakId = userIdentity.getKeycloakId();
        dataAnalysisRunBO.uploadOutput(runId, req, keycloakId);

        return Response.status(Response.Status.CREATED).entity(null).build();
    }

}
