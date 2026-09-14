package de.unihamburg.daibetes.api.model.version;

import bio.cosy.feddb.core.api.model.ModelVersionDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.core.api.model.ModelSubDTO;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubMapper;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.runs.experiment.ExperimentEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


@Mapper(config = QuarkusMappingConfig.class)
public interface ModelVersionMapper extends BaseMapper<ModelVersionDTO, ModelVersionEntity> {

    ModelSubMapper modelSubMapper = Mappers.getMapper(ModelSubMapper.class);

    @Mappings({
            @Mapping(target = "modelId", source = "model.id"),
            @Mapping(target = "modelVersion", source = "versionFormatted"),
            @Mapping(target = "subModels", source = "subModels", qualifiedByName = "mapSubModels"),
            @Mapping(target = "selectedSubModel", ignore = true),
            @Mapping(target = "experimentId", source = "experiment", qualifiedByName = "mapExperimentId"),
            @Mapping(target = "federatedExperimentId", source = "federatedExperiment", qualifiedByName = "mapFedExperimentId"),
    })
    ModelVersionDTO entityToDto(ModelVersionEntity entity);

    @Mappings({
            @Mapping(target = "model.id", source = "modelId"),
            @Mapping(target = "majorVersion", source = "modelVersion"),
            @Mapping(target = "minorVersion", source = "modelVersion"),
            @Mapping(target = "patchVersion", source = "modelVersion"),
            @Mapping(target = "experiment", source = "experimentId", qualifiedByName = "mapExperiment"),
            @Mapping(target = "federatedExperiment", source = "federatedExperimentId", qualifiedByName = "mapFedExperiment"),
            @Mapping(target = "subModels", ignore = true),
    })
    ModelVersionEntity dtoToEntity(ModelVersionDTO dto);

    @Named("mapExperiment")
    default ExperimentEntity mapExperiment(Long experimentId) {
        if (experimentId == null) {
            return null;
        }
        ExperimentEntity experiment = new ExperimentEntity();
        experiment.setId(experimentId);
        return experiment;
    }

    @Named("mapFedExperiment")
    default ProjectFederatedExperimentEntity mapFedExperiment(Long experimentId) {
        if (experimentId == null) {
            return null;
        }
        ProjectFederatedExperimentEntity experiment = new ProjectFederatedExperimentEntity();
        experiment.setId(experimentId);
        return experiment;
    }

    @Named("mapExperimentId")
    default Long mapExperimentId(ExperimentEntity experiment) {
        if (experiment == null) {
            return null;
        }
        return experiment.getId();
    }

    @Named("mapFedExperimentId")
    default Long mapFedExperimentId(ProjectFederatedExperimentEntity experiment) {
        if (experiment == null) {
            return null;
        }
        return experiment.getId();
    }

    @Named("mapSubModels")
    default List<ModelSubDTO> mapSubModels(Set<ModelSubEntity> subModels) {
        if (subModels == null || subModels.isEmpty()) {
            return new ArrayList<>();
        }
        return modelSubMapper.entitiesToDtos(subModels.stream());

    }

    @AfterMapping
    default void setSelectedSubModel(ModelVersionEntity entity, @MappingTarget ModelVersionDTO dto) {
        Set<ModelSubEntity> subModels = entity.getSubModels();
        if (subModels == null || subModels.size() != 1) {
            return;
        }
        ModelSubEntity firstSubModel = subModels.iterator().next();
        ModelSubDTO subModelDTO = modelSubMapper.entityToDto(firstSubModel);
        dto.setSelectedSubModel(subModelDTO);
    }

}
