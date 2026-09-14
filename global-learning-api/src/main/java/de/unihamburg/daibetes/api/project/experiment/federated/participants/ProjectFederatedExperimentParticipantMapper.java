package de.unihamburg.daibetes.api.project.experiment.federated.participants;

import bio.cosy.feddb.core.base.BaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;


@Mapper(config = QuarkusMappingConfig.class)
public interface ProjectFederatedExperimentParticipantMapper extends BaseMapper<ProjectFederatedExperimentParticipantDTO, ProjectFederatedExperimentParticipantEntity> {
    ObjectMapper objectMapper = new ObjectMapper();

    @Mappings({
            @Mapping(target = "experimentId", source = "experiment.id"),
    })
    ProjectFederatedExperimentParticipantDTO entityToDto(ProjectFederatedExperimentParticipantEntity entity);


    @Mappings({
            @Mapping(target = "experiment.id", source = "experimentId")
    })
    ProjectFederatedExperimentParticipantEntity dtoToEntity(ProjectFederatedExperimentParticipantDTO dto);


}
