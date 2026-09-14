package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentAO;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class FederatedLearningExperimentAO extends BaseWorkflowExperimentAO<FederatedLearningExperimentEntity> {

    public Optional<FederatedLearningExperimentEntity> getByGlobalRequestId(String globalRequestId) {
        return find("project.request.globalFLExperimentUniqueId", globalRequestId)
                .firstResultOptional();
    }
}
