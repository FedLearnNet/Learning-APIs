package de.unihamburg.daibetes.api.model.sub.result;

import bio.cosy.feddb.core.api.model.result.ModelExperimentResultDTO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import bio.cosy.feddb.core.api.model.ModelSubDTO;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

public class ModelExperimentResultServiceImpl implements ModelExperimentResultService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    ModelFederatedExperimentResultBO bo;

    @Override
    @Transactional
    public Response upload(ModelExperimentResultDTO req) {
        ModelSubDTO result = bo.uploadResults(req, userIdentity.getKeycloakId());
        return Response.status(Response.Status.CREATED).entity(result).build();
    }
}
