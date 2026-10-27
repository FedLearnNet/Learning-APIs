package bio.cosy.feddb.local.execution.run;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptDTO;
import bio.cosy.feddb.core.execution.file.ExecutionFileDirection;
import bio.cosy.feddb.core.execution.message.*;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepDTO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptDTO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.file.ExecutionFileDTO;
import bio.cosy.feddb.local.execution.file.ExecutionFileEntity;
import bio.cosy.feddb.local.execution.file.ExecutionFileMapper;
import bio.cosy.feddb.local.execution.message.ExecutionMessageEntity;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepDTO;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.*;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(config = QuarkusMappingConfig.class, uses = ExecutionFileMapper.class)
public interface ExecutionRunMapper {

    @Mappings({
            @Mapping(target = "cohortId", source = "cohort.id"),
            @Mapping(target = "connectorRunId", source = "connectorRun.id"),
            @Mapping(target = "steps", source = "steps", qualifiedByName = "mapStepEntitiesToDTOs")
    })
    ExecutionRunDTO mapRunEntityToDTO(ExecutionRunEntity run, @Context boolean withMessages);

    @Mappings({
            @Mapping(target = "runId", source = "run.id"),
            @Mapping(target = "runPublicId", source = "run.publicId"),
            @Mapping(target = "workflowNodeId", source = "workflowNode.id"),
            @Mapping(target = "selectedAttemptId", source = "selectedAttempt.id"),
            @Mapping(target = "transformerId", source = "transformer.id"),
            @Mapping(target = "attempts", ignore = true)
    })
    ExecutionRunStepDTO mapStepEntityToDTO(ExecutionRunStepEntity step);

    @Mappings({
            @Mapping(target = "stepId", source = "step.id"),
            @Mapping(target = "stepPublicId", source = "step.publicId"),
            @Mapping(target = "runId", source = "step.run.id"),
            @Mapping(target = "runPublicId", source = "step.run.publicId"),
            @Mapping(target = "scoreMetric", source = "scoreMessage"),
            @Mapping(target = "lastLog", source = "lastLogMessage.payload.message"),
            @Mapping(target = "inputFiles", source = "executionFiles", qualifiedByName = "mapFileEntitiesToInputDTOs"),
            @Mapping(target = "outputFiles", source = "executionFiles", qualifiedByName = "mapFileEntitiesToOutputDTOs"),
            @Mapping(target = "outputData", ignore = true),
            @Mapping(target = "logs", ignore = true),
            @Mapping(target = "metrics", ignore = true)
    })
    ExecutionAttemptDTO mapAttemptEntityToDTO(ExecutionAttemptEntity attempt, @Context boolean withMessages);

    List<ExecutionFileDTO> mapFileEntitiesToDTOs(List<ExecutionFileEntity> files);

    // Attempts in trial order with their step.
    @Named("mapStepEntitiesToDTOs")
    default List<BaseExecutionRunStepDTO> mapStepEntitiesToDTOs(List<ExecutionRunStepEntity> steps, @Context boolean withMessages) {
        return steps.stream().<BaseExecutionRunStepDTO>map(step -> {
            ExecutionRunStepDTO dto = mapStepEntityToDTO(step);
            dto.setAttempts(step.getAttempts().stream()
                    .sorted(Comparator.comparingInt(ExecutionAttemptEntity::getTrialIndex))
                    .map(attempt -> (BaseExecutionAttemptDTO) mapAttemptEntityToDTO(attempt, withMessages))
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            return dto;
        }).toList();
    }

    @Named("mapFileEntitiesToInputDTOs")
    default List<ExecutionFileDTO> mapFileEntitiesToInputDTOs(List<ExecutionFileEntity> files) {
        return mapFileEntitiesToDTOs(files.stream().filter(file -> file.getDirection() == ExecutionFileDirection.INPUT).toList());
    }

    @Named("mapFileEntitiesToOutputDTOs")
    default List<ExecutionFileDTO> mapFileEntitiesToOutputDTOs(List<ExecutionFileEntity> files) {
        return mapFileEntitiesToDTOs(files.stream().filter(file -> file.getDirection() == ExecutionFileDirection.OUTPUT).toList());
    }

    @AfterMapping
    default void withMessages(ExecutionAttemptEntity attempt, @MappingTarget ExecutionAttemptDTO dto, @Context boolean withMessages) {
        if (withMessages) {
            dto.setLogs(payloadsOf(attempt.getMessages(), RunMessageTypes.LOG, ExecutionMessageLogDTO.class));
            dto.setMetrics(payloadsOf(attempt.getMessages(), RunMessageTypes.METRIC, ExecutionMessageMetricDTO.class));
        }
    }

    private <T extends ExecutionMessageDTO> List<T> payloadsOf(List<ExecutionMessageEntity> messages, RunMessageTypes type, Class<T> payloadType) {
        return messages.stream()
                .filter(message -> message.getType() == type)
                .map(BaseExecutionMessageEntity::getPayload)
                .filter(payloadType::isInstance)
                .map(payloadType::cast)
                .toList();
    }
}
