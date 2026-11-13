package bio.cosy.feddb.core.rest;

import de.unihamburg.daibetes.api.project.experiment.federated.CreateProjectFederatedExperimentDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ProjectFederatedExperimentMapperTest {

    @Inject
    ProjectFederatedExperimentMapper mapper;

    @Test
    void createDtoToDtoMapsPlatformIsCoordinator() {
        CreateProjectFederatedExperimentDTO createDTO = new CreateProjectFederatedExperimentDTO();
        createDTO.setName("Platform Coordinator Experiment");
        createDTO.setDescription("Mapper regression test");
        createDTO.setPlatformIsCoordinator(true);

        ProjectFederatedExperimentDTO dto = mapper.createDtoToDto(createDTO, 1L, "channel-id", "relay-address");

        assertTrue(dto.getPlatformIsCoordinator());
    }

    @Test
    void entityToDtoAndBackRoundTripsPlatformIsCoordinator() {
        ProjectFederatedExperimentEntity entity = new ProjectFederatedExperimentEntity();
        entity.setName("Platform Coordinator Experiment");
        entity.setDescription("Mapper regression test");
        entity.setPlatformIsCoordinator(true);

        ProjectFederatedExperimentDTO dto = mapper.entityToDto(entity);
        assertTrue(dto.getPlatformIsCoordinator());

        ProjectFederatedExperimentEntity roundTripped = mapper.dtoToEntity(dto);
        assertTrue(roundTripped.isPlatformIsCoordinator());
    }
}
