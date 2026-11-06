package de.unihamburg.daibetes.api.project.experiment.federated.aggregator;

import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

/**
 * Default implementation of {@link ProjectFederatedExperimentAggregatorModelService}.
 */
@ApplicationScoped
public class ProjectFederatedExperimentAggregatorModelServiceImpl implements ProjectFederatedExperimentAggregatorModelService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    ProjectFederatedExperimentAggregatorModelBO bo;

    @Override
    @Transactional
    public Response uploadModel(final Long stepId, final ModelSubDataDTO file) {
        bo.uploadModel(stepId, file, userIdentity.getKeycloakId());
        return Response.status(Response.Status.CREATED).entity(null).build();
    }
}
