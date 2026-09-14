package de.unihamburg.daibetes.api.project.experiment.local.data;

import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.file.FileMapper;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

@Mapper(config = QuarkusMappingConfig.class)
public interface ProjectLocalExperimentStepDataMapper extends BaseMapper<ProjectLocalExperimentStepDataDTO, ProjectLocalExperimentStepDataEntity> {

    FileMapper fileMapper = Mappers.getMapper(FileMapper.class);

    @Mappings({
            @Mapping(target = "stepOutputId", source = "stepOutput.id"),
            @Mapping(target = "file", expression = "java(fileMapper.entityToDto(entity.getFile()))"),
            @Mapping(target = "stepInputId", source = "stepInput", qualifiedByName = "mapStepId"),
    })
    ProjectLocalExperimentStepDataDTO entityToDto(ProjectLocalExperimentStepDataEntity entity);

    @Mappings({
            @Mapping(target = "file", expression = "java(fileMapper.dtoToEntity(dto.getFile()))"),
            @Mapping(target = "stepInput.id", source = "stepInputId"),
            @Mapping(target = "stepOutput", source = "stepOutputId", qualifiedByName = "mapStep"),
    })
    ProjectLocalExperimentStepDataEntity dtoToEntity(ProjectLocalExperimentStepDataDTO dto);

    @Named("mapStep")
    default ProjectLocalExperimentStepEntity mapStep(Long stepId) {
        if (stepId == null) {
            return null;
        }
        ProjectLocalExperimentStepEntity step = new ProjectLocalExperimentStepEntity();
        step.setId(stepId);
        return step;
    }

    @Named("mapStepId")
    default Long mapStepId(ProjectLocalExperimentStepEntity step) {
        if (step == null) {
            return null;
        }
        return step.getId();
    }
}
