package bio.cosy.feddb.local.api.learning.project.run.message;

import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class FederatedLearningExperimentStepMessageAO implements PanacheRepository<FederatedLearningExperimentStepMessageEntity> {

    public List<FederatedLearningExperimentStepMessageEntity> findByProject(Long id) {
        return list("step.project.id", id);
    }

    public List<FederatedLearningExperimentStepMessageEntity> findByProject(Long id, RunMessageTypes type) {
        return list("step.project.id = ?1 and type = ?2",
                Sort.by("createdAt", Sort.Direction.Descending),
                id, type);
    }

    public List<FederatedLearningExperimentStepMessageEntity> findByStep(Long id, RunMessageTypes type) {
        return list("step.id = ?1 and type = ?2",
                Sort.by("createdAt", Sort.Direction.Descending),
                id, type);
    }

}
