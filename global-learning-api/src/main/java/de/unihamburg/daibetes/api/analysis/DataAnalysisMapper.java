package de.unihamburg.daibetes.api.analysis;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import bio.cosy.feddb.core.api.model.workflow.DataAnalysisDetailDTO;
import bio.cosy.feddb.core.api.model.workflow.ModelWorkflowDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.DataAnalysisChatWrapperDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageType;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.base.BaseWorkflowEngine;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.analysis.chat.DataAnalysisChatMessageMapper;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileMapper;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionMapper;
import de.unihamburg.daibetes.api.analysis.worklfow.DataAnalysisWorkflowRunEntity;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepEntity;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepMapper;
import de.unihamburg.daibetes.api.workflow.WorkflowMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Mapper(config = QuarkusMappingConfig.class)
public interface DataAnalysisMapper extends BaseMapper<ModelWorkflowDTO, DataAnalysisEntity> {

    DataAnalysisPredictionMapper predictionMapper = Mappers.getMapper(DataAnalysisPredictionMapper.class);
    DataAnalysisFileMapper modelWorkflowFileMapper = Mappers.getMapper(DataAnalysisFileMapper.class);
    DataAnalysisChatMessageMapper modelWorkflowChatMessageMapper = Mappers.getMapper(DataAnalysisChatMessageMapper.class);
    DataAnalysisWorkflowRunStepMapper dataAnalysisWorkflowRunStepMapper = Mappers.getMapper(DataAnalysisWorkflowRunStepMapper.class);
    WorkflowMapper workflowMapper = Mappers.getMapper(WorkflowMapper.class);

    @Mappings({
    })
    ModelWorkflowDTO entityToDto(DataAnalysisEntity entity);

    @Mappings({
            @Mapping(target = "predictions", ignore = true),
            @Mapping(target = "files", ignore = true),
            @Mapping(target = "chatMessages", ignore = true),
            @Mapping(target = "workflowPredictions", ignore = true)
    })
    DataAnalysisEntity dtoToEntity(ModelWorkflowDTO dto);

    @Mappings({
            @Mapping(target = "files", expression = "java(modelWorkflowFileMapper.entitiesToDtos(entity.getFiles()))"),
            @Mapping(target = "messages", expression = "java(mapMessages(entity, false))"),
    })
    DataAnalysisDetailDTO entityToDetail(DataAnalysisEntity entity);

    @Mappings({
            @Mapping(target = "files", expression = "java(modelWorkflowFileMapper.entitiesToDtos(entity.getFiles()))"),
            @Mapping(target = "messages", expression = "java(mapMessages(entity, true))"),
    })
    DataAnalysisDetailDTO entityToDetailFlat(DataAnalysisEntity entity);

    @Mappings({
            @Mapping(target = "currentWorkflowStep", ignore = true),
            @Mapping(target = "currentWorkflowStepId", ignore = true),
            @Mapping(target = "maxWorkflowSteps", ignore = true),
            @Mapping(target = "runMode", ignore = true),
            @Mapping(target = "workflowRunId", ignore = true),
            @Mapping(target = "executionOrder", ignore = true)
    })
    DataAnalysisResultDTO predictionToResult(DataAnalysisPredictionDTO prediction);

    default List<DataAnalysisResultDTO> mapWorkflowPredictions(DataAnalysisEntity entity) {
        Set<DataAnalysisWorkflowRunEntity> workflows = entity.getWorkflowPredictions();
        if (workflows == null) {
            return List.of();
        }
        final BaseWorkflowEngine baseWorkflowEngine = new BaseWorkflowEngine();
        return workflows.stream()
                .map(w -> {
                    DataAnalysisWorkflowRunStepEntity currentNode = w.getCurrentWorkflowNode();
                    if (currentNode != null) {
                        return dataAnalysisWorkflowRunStepMapper.entityToResultDTO(currentNode);
                    }
                    WorkflowDTO workflowDTO = workflowMapper.entityToDto(w.getWorkflow());
                    WorkflowNodeDetailDTO startWorkflowNode = baseWorkflowEngine.firstNode(workflowDTO);
                    if (startWorkflowNode != null) {
                        return w.getSteps().stream()
                                .filter(step -> step.getWorkflowNode().getId().equals(startWorkflowNode.getId()))
                                .findFirst()
                                .map(dataAnalysisWorkflowRunStepMapper::entityToResultDTO)
                                .orElse(null);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    default List<DataAnalysisResultDTO> mapWorkflowPredictionsFlat(DataAnalysisEntity entity) {
        Set<DataAnalysisWorkflowRunEntity> workflows = entity.getWorkflowPredictions();
        if (workflows == null) {
            return List.of();
        }
        return workflows.stream()
                .flatMap(w -> w.getSteps().stream())
                .map(dataAnalysisWorkflowRunStepMapper::entityToResultDTO)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(
                                (DataAnalysisResultDTO o) -> o.getWorkflowRunId() != null ? o.getWorkflowRunId() : 0L)
                        .thenComparingLong(o -> o.getExecutionOrder() != null ? o.getExecutionOrder() : 0))
                .toList();
    }

    default List<DataAnalysisChatWrapperDTO> mapMessages(DataAnalysisEntity entity, boolean flat) {
        List<DataAnalysisPredictionDTO> predictions = predictionMapper.entitiesToDtos(entity.getPredictions());
        List<DataAnalysisResultDTO> workflowPredictions = flat ? mapWorkflowPredictionsFlat(entity) : mapWorkflowPredictions(entity);
        List<ModelWorkflowChatMessageDTO> messages = modelWorkflowChatMessageMapper.entitiesToDtos(entity.getChatMessages());

        Stream<DataAnalysisChatWrapperDTO<DataAnalysisResultDTO>> predictionsWrapped =
                Optional.ofNullable(predictions).stream().flatMap(list -> list.stream()
                        .map(this::predictionToResult)
                        .map(p -> {
                            p.setRunMode(DataAnalysisRunModesEnum.PREDICTION);
                            DataAnalysisChatWrapperDTO<DataAnalysisResultDTO> wrapper = new DataAnalysisChatWrapperDTO<>();
                            wrapper.setMessage(p);
                            wrapper.setType(ModelWorkflowChatMessageType.PREDICTION);
                            return wrapper;
                        }));


        Stream<DataAnalysisChatWrapperDTO<DataAnalysisResultDTO>> workflowPredictionsWrapped =
                Optional.ofNullable(workflowPredictions).stream().flatMap(list -> list.stream()
                        .map(p -> {
                            DataAnalysisChatWrapperDTO<DataAnalysisResultDTO> wrapper = new DataAnalysisChatWrapperDTO<>();
                            wrapper.setMessage(p);
                            wrapper.setType(ModelWorkflowChatMessageType.WORKFLOW_PREDICTION);
                            return wrapper;
                        }));

        Stream<DataAnalysisChatWrapperDTO<ModelWorkflowChatMessageDTO>> messagesWrapped =
                Optional.ofNullable(messages).stream().flatMap(list -> list.stream()
                        .map(p -> {
                            DataAnalysisChatWrapperDTO<ModelWorkflowChatMessageDTO> wrapper = new DataAnalysisChatWrapperDTO<>();
                            wrapper.setMessage(p);
                            wrapper.setType(ModelWorkflowChatMessageType.CHAT_MESSAGE);
                            return wrapper;
                        }));

        return Stream.concat(Stream.concat(predictionsWrapped, workflowPredictionsWrapped), messagesWrapped)
                .sorted((o1, o2) -> {
                    if (o1.getMessage().getCreatedAt() == null || o2.getMessage().getCreatedAt() == null) {
                        return 0;
                    }
                    return o1.getMessage().getCreatedAt().compareTo(o2.getMessage().getCreatedAt());
                }).collect(Collectors.toList());
    }
}
