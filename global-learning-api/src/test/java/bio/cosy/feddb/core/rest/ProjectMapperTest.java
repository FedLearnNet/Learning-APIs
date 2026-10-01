package bio.cosy.feddb.core.rest;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import de.unihamburg.daibetes.api.project.ProjectCreateDTO;
import de.unihamburg.daibetes.api.project.ProjectEntity;
import de.unihamburg.daibetes.api.project.ProjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ProjectMapperTest {

    @Inject
    ProjectMapper mapper;

    @Test
    void createToDtoMapsPlatformIsCoordinator() {
        ProjectCreateDTO createDTO = new ProjectCreateDTO();
        createDTO.setName("Platform Coordinator Project");
        createDTO.setDescription("Mapper regression test");
        createDTO.setPlatformIsCoordinator(true);

        ProjectDetailDTO dto = mapper.createToDTO(createDTO);

        assertTrue(dto.isPlatformIsCoordinator());
    }

    @Test
    void entityToDtoAndBackRoundTripsPlatformIsCoordinator() {
        ProjectEntity entity = new ProjectEntity();
        entity.setName("Platform Coordinator Project");
        entity.setDescription("Mapper regression test");
        entity.setPlatformIsCoordinator(true);

        ProjectDetailDTO dto = mapper.entityToDto(entity);
        assertTrue(dto.isPlatformIsCoordinator());

        ProjectEntity roundTripped = mapper.dtoToEntity(dto);
        assertTrue(Boolean.TRUE.equals(roundTripped.getPlatformIsCoordinator()));
    }
}
