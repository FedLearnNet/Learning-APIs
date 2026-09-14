package de.unihamburg.daibetes.api.runs.test;

import bio.cosy.feddb.core.api.run.StartRunDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.runs.base.HyperParamMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;


@Mapper(config = QuarkusMappingConfig.class)
public interface TestRunMapper extends BaseMapper<TestRunDTO, TestRunEntity> {


    HyperParamMapper hyperParamMapper = Mappers.getMapper(HyperParamMapper.class);

    @Mappings({
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.jsonStringToHyperparams(entity.getHyperParams()))"),
            @Mapping(target = "federatedAppId", source = "federatedAppVersion.federatedApp.id"),
            @Mapping(target = "federatedAppVersionId", source = "federatedAppVersion.id"),
            @Mapping(target = "federatedAppVersionName", source = "federatedAppVersion.versionFormatted"),
            @Mapping(target = "inputData", expression = "java(hyperParamMapper.jsonStringToHyperparamsAllowError(entity.getInput()))"),
            @Mapping(target = "outputData", expression = "java(hyperParamMapper.jsonStringToHyperparamsAllowError(entity.getOutput()))"),
            @Mapping(target = "inputFilePaths", expression = "java(hyperParamMapper.jsonStringToInputPathsAllowError(entity.getInputFilePath()))"),
    })
    TestRunDTO entityToDto(TestRunEntity entity);

    @Mappings({
            @Mapping(target = "federatedAppVersion.id", source = "federatedAppVersionId"),
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.hyperparamsToJsonString(dto.getHyperParams()))"),
            @Mapping(target = "input", expression = "java(hyperParamMapper.hyperparamsToJsonString(dto.getInputData()))"),
            @Mapping(target = "messages", ignore = true),
            @Mapping(target = "output", expression = "java(hyperParamMapper.hyperparamsToJsonStringAllowError(dto.getOutputData()))"),
            @Mapping(target = "inputFilePath", expression = "java(hyperParamMapper.inputPathsToJsonStringAllowError(dto.getInputFilePaths()))")
    })
    TestRunEntity dtoToEntity(TestRunDTO dto);


    StartRunDTO dtoToStartDto(TestRunDTO dto);
}
