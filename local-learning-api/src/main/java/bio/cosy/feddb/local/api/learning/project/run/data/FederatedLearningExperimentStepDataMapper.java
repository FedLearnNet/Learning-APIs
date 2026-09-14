package bio.cosy.feddb.local.api.learning.project.run.data;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.file.FileMapper;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepEntity;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedLearningExperimentStepDataMapper extends BaseMapper<FederatedLearningExperimentStepDataDTO, FederatedLearningExperimentStepDataEntity> {

    FileMapper fileMapper = Mappers.getMapper(FileMapper.class);

    @Mappings({
            @Mapping(target = "stepOutputId", source = "stepOutput.id"),
            @Mapping(target = "file", expression = "java(fileMapper.entityToDto(entity.getFile()))"),
            @Mapping(target = "stepInputId", source = "stepInput", qualifiedByName = "mapStepId"),
    })
    FederatedLearningExperimentStepDataDTO entityToDto(FederatedLearningExperimentStepDataEntity entity);

    @Mappings({
            @Mapping(target = "file",  expression = "java(fileMapper.dtoToEntity(dto.getFile()))"),
            @Mapping(target = "stepInput.id", source = "stepInputId"),
            @Mapping(target = "stepOutput", source = "stepOutputId", qualifiedByName = "mapStep"),
    })
    FederatedLearningExperimentStepDataEntity dtoToEntity(FederatedLearningExperimentStepDataDTO dto);

    @Named("mapStep")
    default FederatedLearningExperimentStepEntity mapStep(Long stepId) {
        if (stepId == null) {
            return null;
        }
        FederatedLearningExperimentStepEntity step = new FederatedLearningExperimentStepEntity();
        step.setId(stepId);
        return step;
    }

    @Named("mapStepId")
    default Long mapStepId(FederatedLearningExperimentStepEntity step) {
        if (step == null) {
            return null;
        }
        return step.getId();
    }
}
