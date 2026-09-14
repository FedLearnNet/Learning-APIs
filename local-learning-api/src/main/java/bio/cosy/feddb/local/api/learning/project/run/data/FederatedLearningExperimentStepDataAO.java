package bio.cosy.feddb.local.api.learning.project.run.data;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class FederatedLearningExperimentStepDataAO implements PanacheRepository<FederatedLearningExperimentStepDataEntity> {

    public List<FederatedLearningExperimentStepDataEntity> findByStep(Long id) {
        return list("stepInput.id = ?1 OR stepOutput.id = ?2", id, id);
    }

}
