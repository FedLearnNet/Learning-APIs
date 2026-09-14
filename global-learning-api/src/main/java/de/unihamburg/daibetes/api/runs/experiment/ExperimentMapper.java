package de.unihamburg.daibetes.api.runs.experiment;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.runs.base.HyperParamMapper;
import de.unihamburg.daibetes.api.runs.experiment.run.ExperimentRunEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


@Mapper(config = QuarkusMappingConfig.class)
public interface ExperimentMapper extends BaseMapper<ExperimentDTO, ExperimentEntity> {
    HyperParamMapper hyperParamMapper = Mappers.getMapper(HyperParamMapper.class);

    ObjectMapper objectMapper = new ObjectMapper();

    @Mappings({
            @Mapping(target = "federatedAppId", source = "federatedAppVersion.federatedApp.id"),
            @Mapping(target = "federatedAppVersionId", source = "federatedAppVersion.id"),
            @Mapping(target = "federatedAppVersionName", source = "federatedAppVersion.versionFormatted"),
            @Mapping(target = "inputData", source = "input"),
            @Mapping(target = "diagramConfigs", expression = "java(jsonStringToDiagramConfig(entity.getDiagramConfig()))"),
            @Mapping(target = "status", expression = "java(mapRunStatusToExperimentStatus(entity.getRuns()))"),
            @Mapping(target = "inputFilePaths", expression = "java(hyperParamMapper.jsonStringToInputPathsAllowError(entity.getInputFilePath()))")
    })
    ExperimentDTO entityToDto(ExperimentEntity entity);

    @Mappings({
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "federatedAppId", ignore = true),
            @Mapping(target = "federatedAppVersionId", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "status", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "federatedAppVersionName", ignore = true)
    })
    ExperimentDTO createToDto(CreateExperimentDTO dto);


    @Mappings({
            @Mapping(target = "federatedAppVersion.id", source = "federatedAppVersionId"),
            @Mapping(target = "input", source = "inputData"),
            @Mapping(target = "diagramConfig", expression = "java(diagramConfigToJsonString(dto.getDiagramConfigs()))"),
            @Mapping(target = "modelVersion", ignore = true),
            @Mapping(target = "runs", ignore = true),
            @Mapping(target = "inputFilePath", expression = "java(hyperParamMapper.inputPathsToJsonStringAllowError(dto.getInputFilePaths()))"),
    })
    ExperimentEntity dtoToEntity(ExperimentDTO dto);

    @Mappings({

            @Mapping(target = "runs", ignore = true),
    })
    ExperimentDetailDTO toDetail(ExperimentDTO entity);

    @Mappings({
            @Mapping(target = "federatedAppVersion.id", source = "federatedAppVersionId"),
            @Mapping(target = "input", source = "inputData"),
            @Mapping(target = "diagramConfig", expression = "java(diagramConfigToJsonString(dto.getDiagramConfigs()))"),
            @Mapping(target = "runs", ignore = true),
            @Mapping(target = "modelVersion", ignore = true),
            @Mapping(target = "inputFilePath", expression = "java(hyperParamMapper.inputPathsToJsonStringAllowError(dto.getInputFilePaths()))"),
    })
    ExperimentEntity detailToEntity(ExperimentDetailDTO dto);


    default List<ExperimentDiagramConfigDTO> jsonStringToDiagramConfig(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(jsonString,
                    objectMapper.getTypeFactory().constructCollectionType(List.class,
                            ExperimentDiagramConfigDTO.class));
        } catch (IOException e) {
            throw new RuntimeException("Error parsing JSON string to LinkedHashMap", e);
        }
    }

    default RunStatusTypes mapRunStatusToExperimentStatus(Set<ExperimentRunEntity> runs) {
        if (runs == null || runs.isEmpty()) {
            return RunStatusTypes.ERROR;
        }

        List<RunStatusTypes> statuses = runs.stream().map(ExperimentRunEntity::getStatus).toList();

        if (statuses.contains(RunStatusTypes.ERROR)) {
            return RunStatusTypes.ERROR;
        }

        if (statuses.contains(RunStatusTypes.RUNNING)) {
            return RunStatusTypes.RUNNING;
        }
        if (statuses.contains(RunStatusTypes.PENDING)) {
            return RunStatusTypes.PENDING;
        }
        if (statuses.stream().allMatch(RunStatusTypes.FINISHED::equals)) {
            return RunStatusTypes.FINISHED;
        }
        return RunStatusTypes.INITIALIZED;
    }

    default String diagramConfigToJsonString(List<ExperimentDiagramConfigDTO> diagramConfigs) {
        if (diagramConfigs == null || diagramConfigs.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(diagramConfigs);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting ExperimentDiagramConfigDTO to JSON string", e);
        }
    }

}
