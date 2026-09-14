package de.unihamburg.daibetes.api.runs.experiment.run;

import de.unihamburg.daibetes.api.runs.base.HyperParamMapper;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.LinkedHashMap;


@Mapper(config = QuarkusMappingConfig.class)
public interface ExperimentRunMapper extends BaseMapper<ExperimentRunDTO, ExperimentRunEntity> {

    HyperParamMapper hyperParamMapper = Mappers.getMapper(HyperParamMapper.class);

    @Mappings({
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.jsonStringToHyperparams(entity.getHyperParams()))"),
            @Mapping(target = "experimentId", source = "experiment.id"),
            @Mapping(target = "outputData", expression = "java(hyperParamMapper.jsonStringToHyperparamsAllowError(entity.getOutput()))"),
    })
    ExperimentRunDTO entityToDto(ExperimentRunEntity entity);

    @Mappings({
            @Mapping(target = "experiment.id", source = "experimentId"),
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.hyperparamsToJsonString(dto.getHyperParams()))"),
            @Mapping(target = "messages", ignore = true),
            @Mapping(target = "modelSub", ignore = true),
            @Mapping(target = "output", expression = "java(hyperParamMapper.hyperparamsToJsonStringAllowError(dto.getOutputData()))"),
    })
    ExperimentRunEntity dtoToEntity(ExperimentRunDTO dto);

    @Mappings({
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.jsonStringToHyperparams(entity.getHyperParams()))"),
            @Mapping(target = "experimentId", source = "experiment.id"),
            @Mapping(target = "inputData", expression = "java(hyperParamMapper.jsonStringToHyperparamsAllowError(entity.getExperiment().getInput()))"),
            @Mapping(target = "outputData", ignore = true),
            @Mapping(target = "inputFilePaths", expression = "java(hyperParamMapper.jsonStringToInputPathsAllowError(entity.getExperiment().getInputFilePath()))")
    })
    UpdateExperimentRunDTO entityToUpdateDto(ExperimentRunEntity entity);

    @Mappings({
            @Mapping(target = "experiment.id", source = "experimentId"),
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.hyperparamsToJsonString(dto.getHyperParams()))"),
            @Mapping(target = "messages", ignore = true),
            @Mapping(target = "modelSub", ignore = true),
            @Mapping(target = "output", expression = "java(hyperParamMapper.hyperparamsToJsonStringAllowError(dto.getOutputData()))"),
    })
    ExperimentRunEntity updateDtoToEntity(UpdateExperimentRunDTO dto);


    default String hyperparamsToJsonStringAllowError(LinkedHashMap<String, Object> hyperParams) {
        return hyperParamMapper.hyperparamsToJsonStringAllowError(hyperParams);
    }
}


