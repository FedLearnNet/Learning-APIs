package de.unihamburg.daibetes.execution.experiment;

import bio.cosy.feddb.core.api.socket.ProjectFederatedExperimentForLocalDTO;
import de.unihamburg.daibetes.api.project.training.federated.CreateProjectFederatedExperimentDTO;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionExperimentMapperTest {

    private final ExecutionExperimentMapper mapper = new ExecutionExperimentMapperImpl();

    private CreateProjectFederatedExperimentDTO create(Boolean platformIsCoordinator) {
        CreateProjectFederatedExperimentDTO create = new CreateProjectFederatedExperimentDTO();
        create.setName("Platform Coordinator Experiment");
        create.setDescription("Mapper regression test");
        create.setPlatformIsCoordinator(platformIsCoordinator);
        return create;
    }

    @Test
    void federatedCreateKeepsPlatformIsCoordinator() {
        ExecutionExperimentEntity experiment = mapper.federatedCreateDtoToEntity(create(true), "test", null, null, null, "relay", "channel");

        assertTrue(experiment.isPlatformIsCoordinator());
        assertTrue(mapper.entityToDto(experiment).isPlatformIsCoordinator());
    }

    @Test
    void federatedCreateWithoutChoiceLetsAClinicCoordinate() {
        assertFalse(mapper.federatedCreateDtoToEntity(create(null), "test", null, null, null, "relay", "channel").isPlatformIsCoordinator());
    }

    @Test
    void clinicsLearnWhoAggregatesBeforeTheyAccept() {
        ExecutionExperimentEntity experiment = mapper.federatedCreateDtoToEntity(create(true), "test", null, null, null, "relay", "channel");

        ProjectFederatedExperimentForLocalDTO query = mapper.experimentToLearningQuery(experiment, Set.of(), null);

        assertTrue(query.getPlatformIsCoordinator());
    }
}
