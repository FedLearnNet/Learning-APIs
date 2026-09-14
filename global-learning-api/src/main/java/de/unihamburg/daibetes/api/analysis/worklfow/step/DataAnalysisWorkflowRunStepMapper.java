package de.unihamburg.daibetes.api.analysis.worklfow.step;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import bio.cosy.feddb.core.base.BaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.analysis.DataAnalysisResultDTO;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileMapper;
import de.unihamburg.daibetes.api.analysis.worklfow.message.DataAnalysisWorkflowRunMessagesEntity;
import de.unihamburg.daibetes.api.analysis.worklfow.message.DataAnalysisWorkflowRunMessagesMapper;
import de.unihamburg.daibetes.api.runs.base.HyperParamMapper;
import de.unihamburg.daibetes.api.runs.base.message.RunMessageLogMapper;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;


@Mapper(config = QuarkusMappingConfig.class)
public interface DataAnalysisWorkflowRunStepMapper extends BaseMapper<DataAnalysisWorkflowRunStepDTO, DataAnalysisWorkflowRunStepEntity> {
    HyperParamMapper hyperParamMapper = Mappers.getMapper(HyperParamMapper.class);
    DataAnalysisFileMapper fileMapper = Mappers.getMapper(DataAnalysisFileMapper.class);
    DataAnalysisWorkflowRunMessagesMapper messageMapper = Mappers.getMapper(DataAnalysisWorkflowRunMessagesMapper.class);
    RunMessageLogMapper logMapper = Mappers.getMapper(RunMessageLogMapper.class);

    ObjectMapper objectMapper = new ObjectMapper();

    @Mappings({
            @Mapping(target = "experimentId", source = "experiment.id"),
            @Mapping(target = "workflowNodeId", source = "workflowNode.id"),
            @Mapping(target = "metrics", ignore = true),
            @Mapping(target = "inputs", expression = "java(hyperParamMapper.jsonStringToHyperparamsAllowError(entity.getInputs()))"),
            @Mapping(target = "result", expression = "java(addFilePredictions(entity))"),
            @Mapping(target = "outputFiles", expression = "java(fileMapper.entitiesToDtos(entity.getOutputFiles()))"),
            @Mapping(target = "inputFiles", expression = "java(fileMapper.entitiesToDtos(entity.getInputFiles()))"),
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.jsonStringToHyperparamsAllowError(entity.getWorkflowNode().getHyperParams()))"),
            @Mapping(target = "lastLog", source = "entity", qualifiedByName = "getLatestLog"),
            @Mapping(target = "logs", source = "entity.messages", qualifiedByName = "mapRunLogs"),
    })
    DataAnalysisWorkflowRunStepDTO entityToDto(DataAnalysisWorkflowRunStepEntity entity);


    @Mappings({
            @Mapping(target = "inputs", expression = "java(hyperParamMapper.jsonStringToHyperparamsAllowError(entity.getInputs()))"),
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.jsonStringToHyperparamsAllowError(entity.getWorkflowNode().getHyperParams()))"),
            @Mapping(target = "result", expression = "java(addFilePredictions(entity))"),
            @Mapping(target = "outputFiles", expression = "java(fileMapper.entitiesToDtos(entity.getOutputFiles()))"),
            @Mapping(target = "inputFiles", expression = "java(fileMapper.entitiesToDtos(entity.getInputFiles()))"),
            @Mapping(target = "lastLog", source = "entity", qualifiedByName = "getLatestLog"),

            @Mapping(target = "status", source = "entity.stepStatus"),
            @Mapping(target = "lastError", source = "entity.lastError"),
            @Mapping(target = "containerId", source = "entity.containerId"),

            @Mapping(target = "workflowRunId", source = "entity.experiment.id"),

            @Mapping(target = "currentWorkflowStepId", source = "entity.id"),

            //after mapping will set the following fields
            @Mapping(target = "runMode", ignore = true),
            @Mapping(target = "maxWorkflowSteps", ignore = true),
            @Mapping(target = "modelId", ignore = true),
            @Mapping(target = "modelSubId", ignore = true),
            @Mapping(target = "modelVersionId", ignore = true),
            @Mapping(target = "appVersionId", ignore = true),
            @Mapping(target = "imageName", ignore = true),
            @Mapping(target = "currentWorkflowStep", ignore = true),
            @Mapping(target = "workflowId", ignore = true),
            @Mapping(target = "name", ignore = true),
            @Mapping(target = "keycloakId", ignore = true),
            @Mapping(target = "dataAnalysisId", ignore = true),
            @Mapping(target = "rawLog", ignore = true),
            @Mapping(target = "executionOrder", source = "entity.workflowNode.executionOrder"),
    })
    DataAnalysisResultDTO entityToResultDTO(DataAnalysisWorkflowRunStepEntity entity);

    @Mappings({
            @Mapping(target = "experiment.id", source = "experimentId"),
            @Mapping(target = "messages", ignore = true),
            @Mapping(target = "workflowNode.id", source = "workflowNodeId"),
            @Mapping(target = "files", ignore = true),
            @Mapping(target = "inputFiles", ignore = true),
            @Mapping(target = "outputFiles", ignore = true),
            @Mapping(target = "inputs", expression = "java(hyperParamMapper.hyperparamsToJsonStringAllowError(dto.getInputs()))"),
            @Mapping(target = "result", expression = "java(hyperParamMapper.hyperparamsToJsonStringAllowError(dto.getResult()))"),
    })
    DataAnalysisWorkflowRunStepEntity dtoToEntity(DataAnalysisWorkflowRunStepDTO dto);


    @Named("getLatestLog")
    default String getLatestLog(DataAnalysisWorkflowRunStepEntity entity) {
        if (entity.getMessages() != null && !entity.getMessages().isEmpty()) {
            return entity.getMessages().stream()
                    .findFirst()
                    .map(DataAnalysisWorkflowRunMessagesEntity::getMessage)
                    .orElse(null);
        }
        return null;
    }

    @Named("mapRunLogs")
    default List<RunMessageLogDTO> mapRunLogs(Set<DataAnalysisWorkflowRunMessagesEntity> messages) {
        return messages.stream()
                .filter(m -> m.getType().equals(RunMessageTypes.LOG))
                .map(messageMapper::entityToDto)
                .map(logMapper::dtoToLogDTO)
                .toList();
    }

    @AfterMapping
    default void enrichResultDTO(@MappingTarget DataAnalysisResultDTO dto,
                                 DataAnalysisWorkflowRunStepEntity entity) {
        WorkflowNodeEntity node = entity.getWorkflowNode();
        WorkflowEntity workflow = node.getWorkflow();

        dto.setRunMode(DataAnalysisRunModesEnum.WORKFLOW);
        dto.setWorkflowId(workflow.getId());
        dto.setName(workflow.getName());
        dto.setDataAnalysisId(entity.getExperiment().getDataAnalysis().getId());
        dto.setKeycloakId(entity.getExperiment().getKeycloakId());

        if (node.getSubModel() != null) {
            dto.setModelId(node.getSubModel().getModelVersion().getModel().getId());
            dto.setModelSubId(node.getSubModel().getId());
            dto.setModelVersionId(node.getSubModel().getModelVersion().getId());
            dto.setAppVersionId(node.getSubModel().getModelVersion().getModel().getFederatedAppVersion().getId());
            dto.setImageName(node.getSubModel().getImageName());
        } else if (node.getFederatedAppVersion() != null) {
            dto.setAppVersionId(node.getFederatedAppVersion().getId());
            dto.setImageName(node.getFederatedAppVersion().getImageName());
        }

        Set<WorkflowNodeEntity> nodes = workflow.getNodes();
        Long total = (long) nodes.size();
        dto.setMaxWorkflowSteps(total);
        long doneSteps = entity.getExperiment()
                .getSteps()
                .stream()
                .filter(s -> s.getStepStatus().equals(RunStatusTypes.FINISHED))
                .count();
        if (!entity.getStepStatus().equals(RunStatusTypes.FINISHED)) {
            doneSteps += 1;
        }
        dto.setCurrentWorkflowStep(doneSteps);
    }

    default LinkedHashMap<String, Object> addFilePredictions(DataAnalysisWorkflowRunStepEntity entity) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        String jsonString = entity.getResult();
        if (!(jsonString == null || jsonString.isEmpty())) {
            try {
                result = objectMapper.readValue(jsonString, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
            } catch (IOException e) {
                return result;
            }
        }
        return result;
    }
}
