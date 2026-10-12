package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerCombiner;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@ApplicationScoped
public class HarmonizationBO {

    @Inject
    HarmonizationAO ao;

    @Inject
    HarmonizationMapper mapper;

    @Inject
    HarmonizationFileBO fileBO;

    @Inject
    TransformerCombiner combiner;

    public List<HarmonizationDTO> list(String keycloakId) {
        return mapper.entitiesToDtos(ao.getAll(keycloakId));
    }

    public HarmonizationDTO getById(Long id, String keycloakId) {
        return mapper.entityToDto(find(id, keycloakId));
    }

    public HarmonizationDTO create(HarmonizationDTO dto, String keycloakId) {
        checkFiles(dto, keycloakId);
        combine(dto);
        HarmonizationEntity entity = mapper.dtoToEntity(dto);
        entity.setId(null);
        entity.setKeycloakId(keycloakId);
        ao.persist(entity);
        return mapper.entityToDto(entity);
    }

    public HarmonizationDTO update(Long id, HarmonizationDTO dto, String keycloakId) {
        checkFiles(dto, keycloakId);
        combine(dto);
        HarmonizationEntity entity = find(id, keycloakId);
        mapper.update(dto, entity);
        return mapper.entityToDto(entity);
    }

    public void setOutputFile(Long id, Long fileId, String keycloakId) {
        find(id, keycloakId).setOutputFileId(fileId);
    }

    public void delete(Long id, String keycloakId) {
        ao.delete(find(id, keycloakId));
    }

    private void combine(HarmonizationDTO dto) {
        dto.setTransformer(combiner.combine(dto.getTransformer()));
    }

    private HarmonizationEntity find(Long id, String keycloakId) {
        return ao.find("id = ?1 and keycloakId = ?2", id, keycloakId).firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Harmonization " + id + " not found"));
    }

    private void checkFiles(HarmonizationDTO dto, String keycloakId) {
        Stream.of(dto.getReferenceFileId(), dto.getSourceFileId())
                .filter(Objects::nonNull)
                .forEach(fileId -> fileBO.getById(fileId, keycloakId));
    }
}
