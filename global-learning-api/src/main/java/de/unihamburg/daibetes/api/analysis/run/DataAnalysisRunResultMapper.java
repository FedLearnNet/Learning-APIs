package de.unihamburg.daibetes.api.analysis.run;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import bio.cosy.feddb.core.api.model.workflow.DataAnalysisFileDTO;
import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptDTO;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.message.*;
import bio.cosy.feddb.core.execution.run.dto.RunMetaDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.analysis.DataAnalysisResultDTO;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileAO;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileEntity;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileMapper;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptMapper;
import de.unihamburg.daibetes.execution.message.ExecutionMessageAO;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepDTO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import io.quarkus.logging.Log;
import de.unihamburg.daibetes.config.FLNetConfig;
import jakarta.inject.Inject;
import org.mapstruct.*;

import java.util.*;
import java.util.stream.Collectors;

import static de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunResultBO.FILE_PREFIX;

/**
 * A run of the execution as the frontend shows it: steps in execution order, each attempt with what its
 * app read, reported and wrote. The report, the result analyzer and the model store read a run as one
 * analysis result instead: a single app or model like a prediction, a workflow by its current step.
 */
@Mapper(config = QuarkusMappingConfig.class, uses = ExecutionAttemptMapper.class)
public abstract class DataAnalysisRunResultMapper {

    @Inject
    FLNetConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ExecutionMessageAO messageAO;

    @Inject
    DataAnalysisFileAO fileAO;

    @Inject
    DataAnalysisFileMapper fileMapper;

    @Inject
    ExecutionRunStepMapper stepMapper;

    @Mappings({
            @Mapping(target = "dataAnalysisId", source = "dataAnalysis.id"),
            @Mapping(target = "workflowId", source = "steps", qualifiedByName = "mapStepEntitiesToWorkflowId"),
            @Mapping(target = "steps", source = "steps", qualifiedByName = "mapStepEntitiesToResultDTOs")
    })
    public abstract DataAnalysisRunDTO mapRunEntityToResultDTO(ExecutionRunEntity run, @Context boolean withMessages);

    @Mappings({
            @Mapping(target = "stepId", source = "step.id"),
            @Mapping(target = "stepPublicId", source = "step.publicId"),
            @Mapping(target = "runId", source = "step.run.id"),
            @Mapping(target = "runPublicId", source = "step.run.publicId"),
            @Mapping(target = "scoreMetric", source = "scoreMessage"),
            @Mapping(target = "meta", source = "meta", qualifiedByName = "mapMetaToVisibleMetaDTO"),
            @Mapping(target = "outputData", source = "result", qualifiedByName = "mapJsonToFields"),
            @Mapping(target = "inputFiles", source = "step.inputs", qualifiedByName = "mapInputsToFileDTOs"),
            @Mapping(target = "outputFiles", source = "files", qualifiedByName = "mapFileEntitiesToOutputDTOs"),
            @Mapping(target = "lastLog", source = "id", qualifiedByName = "mapAttemptIdToLastLog"),
            @Mapping(target = "logs", ignore = true),
            @Mapping(target = "metrics", ignore = true)
    })
    public abstract DataAnalysisAttemptDTO mapAttemptEntityToResultDTO(ExecutionAttemptEntity attempt, @Context boolean withMessages);

    /** The run by its first step not done, else its last; null for a run without steps. */
    public DataAnalysisResultDTO mapRunEntityToAnalysisResultDTO(ExecutionRunEntity run) {
        List<ExecutionRunStepEntity> steps = inExecutionOrder(run.getSteps());
        return steps.stream()
                .filter(step -> !RunStatusTypes.isFinalStatus(step.getStatus()))
                .findFirst()
                .or(() -> steps.stream().reduce((previous, last) -> last))
                .map(this::mapStepEntityToAnalysisResultDTO)
                .orElse(null);
    }

    /** Every step of the run as an analysis result of its own, in execution order. */
    public List<DataAnalysisResultDTO> mapRunEntityToAnalysisResultDTOs(ExecutionRunEntity run) {
        return inExecutionOrder(run.getSteps()).stream().map(this::mapStepEntityToAnalysisResultDTO).toList();
    }

    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "createdAt", source = "run.createdAt"),
            @Mapping(target = "updatedAt", source = "updatedAt"),
            @Mapping(target = "inputs", source = "inputs", defaultExpression = "java(new java.util.LinkedHashMap<>())"),
            @Mapping(target = "inputFiles", source = "inputs", qualifiedByName = "mapInputsToFileDTOs")
    })
    public abstract DataAnalysisResultDTO mapStepEntityToAnalysisResultDTO(ExecutionRunStepEntity step);

    // The step's run is only typed by its base, so fields of the concrete run are mapped from it directly.
    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "keycloakId", source = "keycloakId"),
            @Mapping(target = "dataAnalysisId", source = "dataAnalysis.id")
    })
    protected abstract void mapRunEntityToAnalysisResultDTO(ExecutionRunEntity run, @MappingTarget DataAnalysisResultDTO dto);

    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "hyperParams", source = "params"),
            @Mapping(target = "result", source = "result", qualifiedByName = "mapJsonToFields"),
            @Mapping(target = "containerId", source = "containerId"),
            @Mapping(target = "outputFiles", source = "files", qualifiedByName = "mapFileEntitiesToOutputDTOs"),
            @Mapping(target = "lastLog", source = "id", qualifiedByName = "mapAttemptIdToLastLog"),
            @Mapping(target = "meta", source = "meta", qualifiedByName = "mapMetaToVisibleMetaDTO")
    })
    protected abstract void mapAttemptEntityToAnalysisResultDTO(ExecutionAttemptEntity attempt, @MappingTarget DataAnalysisResultDTO dto);

    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "modelSubId", source = "id"),
            @Mapping(target = "modelVersionId", source = "modelVersion.id"),
            @Mapping(target = "modelId", source = "modelVersion.model.id"),
            @Mapping(target = "appVersionId", source = "modelVersion.model.federatedAppVersion.id"),
            @Mapping(target = "imageName", source = "imageName"),
            @Mapping(target = "name", source = "modelVersion.model.name")
    })
    protected abstract void mapSubModelEntityToAnalysisResultDTO(ModelSubEntity subModel, @MappingTarget DataAnalysisResultDTO dto);

    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "appVersionId", source = "id"),
            @Mapping(target = "imageName", source = "imageName"),
            @Mapping(target = "name", source = "federatedApp.name")
    })
    protected abstract void mapAppVersionEntityToAnalysisResultDTO(FederatedAppVersionEntity appVersion, @MappingTarget DataAnalysisResultDTO dto);

    @AfterMapping
    protected void withMessages(ExecutionAttemptEntity attempt, @MappingTarget DataAnalysisAttemptDTO dto,
                                @Context boolean withMessages) {
        if (withMessages) {
            dto.setLogs(payloadsOf(attempt.getId(), RunMessageTypes.LOG, ExecutionMessageLogDTO.class));
            dto.setMetrics(payloadsOf(attempt.getId(), RunMessageTypes.METRIC, ExecutionMessageMetricDTO.class));
        }
    }

    @AfterMapping
    protected void withAttemptAndTarget(ExecutionRunStepEntity step, @MappingTarget DataAnalysisResultDTO dto) {
        ExecutionRunEntity run = step.getRun();
        ExecutionAttemptEntity attempt = displayedAttemptOf(step);
        WorkflowNodeEntity node = step.getWorkflowNode();

        // A single app or model is addressed by its run, a workflow step by itself.
        dto.setId(node == null ? run.getId() : step.getId());
        mapRunEntityToAnalysisResultDTO(run, dto);
        dto.setRunMode(node == null ? DataAnalysisRunModesEnum.PREDICTION : DataAnalysisRunModesEnum.WORKFLOW);
        dto.setStatus(RunStatusTypes.isFinalStatus(step.getStatus()) || attempt == null ? step.getStatus() : attempt.getStatus());
        dto.setLastError(Optional.ofNullable(step.getFailure())
                .or(() -> Optional.ofNullable(attempt).map(ExecutionAttemptEntity::getFailure))
                .map(ExecutionFailure::getMessage)
                .orElse(null));
        // Mutable, the attempt is merged into them.
        dto.setHyperParams(new LinkedHashMap<>());
        dto.setResult(new LinkedHashMap<>());
        dto.setOutputFiles(new ArrayList<>());
        mapAttemptEntityToAnalysisResultDTO(attempt, dto);

        ModelSubEntity subModel = node == null ? step.getSubModel() : node.getSubModel();
        if (subModel != null) {
            mapSubModelEntityToAnalysisResultDTO(subModel, dto);
        } else {
            mapAppVersionEntityToAnalysisResultDTO(node == null ? step.getFederatedAppVersion() : node.getFederatedAppVersion(), dto);
        }

        if (node != null) {
            withWorkflowPosition(dto, step, run, node);
        } else if (dto.getName() == null) {
            dto.setName(run.getName());
        }
    }

    // Each step with its attempts, which come in trial order.
    @Named("mapStepEntitiesToResultDTOs")
    protected List<BaseExecutionRunStepDTO> mapStepEntitiesToResultDTOs(List<ExecutionRunStepEntity> steps,
                                                                        @Context boolean withMessages) {
        return inExecutionOrder(steps).stream().<BaseExecutionRunStepDTO>map(step -> {
            ExecutionRunStepDTO dto = stepMapper.entityToDto(step);
            dto.setAttempts(step.getAttempts().stream()
                    .map(attempt -> (BaseExecutionAttemptDTO) mapAttemptEntityToResultDTO(attempt, withMessages))
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            return dto;
        }).toList();
    }

    @Named("mapStepEntitiesToWorkflowId")
    protected Long mapStepEntitiesToWorkflowId(List<ExecutionRunStepEntity> steps) {
        return steps.stream()
                .map(ExecutionRunStepEntity::getWorkflowNode)
                .filter(Objects::nonNull)
                .map(node -> node.getWorkflow().getId())
                .findFirst()
                .orElse(null);
    }

    /** The files the inputs refer to, each under its input name. */
    @Named("mapInputsToFileDTOs")
    protected List<DataAnalysisFileDTO> mapInputsToFileDTOs(Map<String, Object> inputs) {
        if (inputs == null) {
            return new ArrayList<>();
        }
        List<DataAnalysisFileDTO> files = new ArrayList<>();
        inputs.forEach((name, value) -> mapReferenceToFileEntity(value).ifPresent(file -> {
            DataAnalysisFileDTO dto = fileMapper.entityToDto(file);
            dto.setInputName(name);
            dto.setOutputName(null);
            files.add(dto);
        }));
        return files;
    }

    @Named("mapFileEntitiesToOutputDTOs")
    protected List<DataAnalysisFileDTO> mapFileEntitiesToOutputDTOs(List<DataAnalysisFileEntity> files) {
        return files.stream()
                .filter(file -> file.getOutputName() != null)
                .map(fileMapper::entityToDto)
                .toList();
    }

    /** The uploaded result, parsed; empty when there is none or it cannot be read. */
    @Named("mapJsonToFields")
    protected LinkedHashMap<String, Object> mapJsonToFields(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    // A copy, stripping overhead from the stored meta would be written back.
    @Named("mapMetaToVisibleMetaDTO")
    protected RunMetaDTO mapMetaToVisibleMetaDTO(RunMetaDTO meta) {
        return meta == null ? null : objectMapper.convertValue(meta, RunMetaDTO.class).withOverheadVisibility(config.runtime().overheadEnabled());
    }

    @Named("mapAttemptIdToLastLog")
    protected String mapAttemptIdToLastLog(Long attemptId) {
        return messageAO.findLatest(attemptId, RunMessageTypes.LOG)
                .map(BaseExecutionMessageEntity::getPayload)
                .map(ExecutionMessageDTO::getMessage)
                .orElse(null);
    }

    /** The analysis file an input value refers to; empty for a plain value or an unknown file. */
    @Named("mapReferenceToFileEntity")
    public Optional<DataAnalysisFileEntity> mapReferenceToFileEntity(Object value) {
        if (!(value instanceof String reference) || !reference.startsWith(FILE_PREFIX)) {
            return Optional.empty();
        }
        try {
            return fileAO.findByIdOptional(Long.parseLong(reference.substring(FILE_PREFIX.length())));
        } catch (NumberFormatException e) {
            Log.warnf("Invalid file reference %s", reference);
            return Optional.empty();
        }
    }

    private <T extends ExecutionMessageDTO> List<T> payloadsOf(Long attemptId, RunMessageTypes type, Class<T> payloadType) {
        return messageAO.listForAttempt(attemptId, type).stream()
                .map(BaseExecutionMessageEntity::getPayload)
                .filter(payloadType::isInstance)
                .map(payloadType::cast)
                .toList();
    }

    private static void withWorkflowPosition(DataAnalysisResultDTO dto, ExecutionRunStepEntity step,
                                             ExecutionRunEntity run, WorkflowNodeEntity node) {
        dto.setWorkflowId(node.getWorkflow().getId());
        dto.setName(node.getWorkflow().getName());
        dto.setWorkflowRunId(run.getId());
        dto.setCurrentWorkflowStepId(step.getId());
        dto.setExecutionOrder(node.getExecutionOrder() != null ? node.getExecutionOrder().longValue() : null);
        dto.setMaxWorkflowSteps((long) run.getSteps().size());
        long doneSteps = run.getSteps().stream().filter(s -> s.getStatus() == RunStatusTypes.FINISHED).count();
        if (step.getStatus() != RunStatusTypes.FINISHED) {
            doneSteps += 1;
        }
        dto.setCurrentWorkflowStep(doneSteps);
    }

    // The selected attempt once the step is done, otherwise the latest one.
    private static ExecutionAttemptEntity displayedAttemptOf(ExecutionRunStepEntity step) {
        if (step.getSelectedAttempt() != null) {
            return step.getSelectedAttempt();
        }
        return step.getAttempts().stream()
                .max(Comparator.comparingInt(ExecutionAttemptEntity::getTrialIndex))
                .orElse(null);
    }

    // Same order the workflow coordinator runs them in; steps without a node last, by id.
    private static List<ExecutionRunStepEntity> inExecutionOrder(Collection<ExecutionRunStepEntity> steps) {
        return steps.stream()
                .sorted(Comparator
                        .comparing((ExecutionRunStepEntity step) -> step.getWorkflowNode() == null ? null : step.getWorkflowNode().getExecutionOrder(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(BaseEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
