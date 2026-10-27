package de.unihamburg.daibetes.execution.run;

import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptDetailDTO;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptDTO;
import bio.cosy.feddb.core.execution.file.ExecutionFileDirection;
import bio.cosy.feddb.core.execution.message.*;
import bio.cosy.feddb.core.execution.run.dto.RunMetaDTO;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptMapper;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentMapper;
import de.unihamburg.daibetes.execution.file.ExecutionFileDTO;
import de.unihamburg.daibetes.execution.file.ExecutionFileEntity;
import de.unihamburg.daibetes.execution.file.ExecutionFileMapper;
import de.unihamburg.daibetes.execution.federation.ExecutionFederationMapper;
import de.unihamburg.daibetes.execution.message.ExecutionMessageEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepDTO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import de.unihamburg.daibetes.config.FLNetConfig;
import jakarta.inject.Inject;
import org.mapstruct.*;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Mapper(config = QuarkusMappingConfig.class, uses = {ExecutionAttemptMapper.class, ExecutionExperimentMapper.class, ExecutionFederationMapper.class})
public abstract class ExecutionRunDetailMapper {

    @Inject
    FLNetConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ExecutionRunStepMapper stepMapper;

    @Inject
    ExecutionFileMapper fileMapper;

    @Mappings({
            @Mapping(target = "steps", source = "steps", qualifiedByName = "mapStepEntitiesToDetailDTOs"),
            @Mapping(target = "experimentId", source = "experiment.id"),
            @Mapping(target = "federatedAppId", ignore = true),
            @Mapping(target = "federatedAppVersionId", ignore = true),
            @Mapping(target = "federatedAppVersionName", ignore = true)
    })
    public abstract ExecutionRunDetailDTO mapRunEntityToDetailDTO(ExecutionRunEntity run, @Context boolean withMessages);

    @Mappings({
            @Mapping(target = "stepId", source = "step.id"),
            @Mapping(target = "stepPublicId", source = "step.publicId"),
            @Mapping(target = "runId", source = "step.run.id"),
            @Mapping(target = "runPublicId", source = "step.run.publicId"),
            @Mapping(target = "scoreMetric", source = "scoreMessage"),
            @Mapping(target = "meta", source = "meta", qualifiedByName = "mapMetaToVisibleMetaDTO"),
            @Mapping(target = "outputData", source = "result", qualifiedByName = "mapJsonToFields"),
            @Mapping(target = "lastLog", source = "lastLogMessage.payload.message"),
            @Mapping(target = "participantId", source = "federation.participant.participantId"),
            @Mapping(target = "currentRound", source = "federation.currentRound"),
            @Mapping(target = "messagesSent", source = "federation.messagesSent"),
            @Mapping(target = "messagesReceived", source = "federation.messagesReceived"),
            @Mapping(target = "waitingFor", source = "federation.waitingFor"),
            @Mapping(target = "logs", ignore = true),
            @Mapping(target = "metrics", ignore = true),
            @Mapping(target = "rounds", ignore = true),
            @Mapping(target = "inputFiles", source = "executionFiles", qualifiedByName = "mapFileEntitiesToInputDTOs"),
            @Mapping(target = "outputFiles", source = "executionFiles", qualifiedByName = "mapFileEntitiesToOutputDTOs")
    })
    public abstract ExecutionAttemptDetailDTO mapAttemptEntityToDetailDTO(ExecutionAttemptEntity attempt, @Context boolean withMessages);

    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "federatedAppId", source = "federatedApp.id"),
            @Mapping(target = "federatedAppVersionId", source = "id")
    })
    public abstract void mapAppVersionToStartRunDTO(FederatedAppVersionEntity version, @MappingTarget StartRunDTO start);

    // A run of a single app runs one app version, the one of its step.
    @AfterMapping
    protected void withAppVersion(ExecutionRunEntity run, @MappingTarget ExecutionRunDetailDTO dto) {
        run.getSteps().stream()
                .map(ExecutionRunStepEntity::getFederatedAppVersion)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(version -> withAppVersion(version, dto));
    }

    @AfterMapping
    protected void withMessages(ExecutionAttemptEntity attempt, @MappingTarget ExecutionAttemptDetailDTO dto,
                                @Context boolean withMessages) {
        if (withMessages) {
            dto.setLogs(payloadsOf(attempt.getMessages(), RunMessageTypes.LOG, ExecutionMessageLogDTO.class));
            dto.setMetrics(payloadsOf(attempt.getMessages(), RunMessageTypes.METRIC, ExecutionMessageMetricDTO.class));
            dto.setRounds(payloadsOf(attempt.getMessages(), RunMessageTypes.ROUND, ExecutionMessageRoundDTO.class));
        }
    }

    // Steps by id, the order they were created in; attempts come in trial order with their step.
    @Named("mapStepEntitiesToDetailDTOs")
    protected List<BaseExecutionRunStepDTO> mapStepEntitiesToDetailDTOs(List<ExecutionRunStepEntity> steps,
                                                                          @Context boolean withMessages) {
        return steps.stream().<BaseExecutionRunStepDTO>map(step -> {
            ExecutionRunStepDTO dto = stepMapper.entityToDto(step);
            dto.setAttempts(step.getAttempts().stream()
                    .map(attempt -> (BaseExecutionAttemptDTO) mapAttemptEntityToDetailDTO(attempt, withMessages))
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            return dto;
        }).toList();
    }

    public String mapFieldsToJson(LinkedHashMap<String, Object> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Output is not valid JSON", e);
        }
    }

    /** The uploaded output, parsed; empty when there is none or it cannot be read. */
    @Named("mapJsonToFields")
    public LinkedHashMap<String, Object> mapJsonToFields(String json) {
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

    @Named("mapFileEntitiesToInputDTOs")
    protected List<ExecutionFileDTO> mapFileEntitiesToInputDTOs(List<ExecutionFileEntity> files) {
        return filesOf(files, ExecutionFileDirection.INPUT);
    }

    @Named("mapFileEntitiesToOutputDTOs")
    protected List<ExecutionFileDTO> mapFileEntitiesToOutputDTOs(List<ExecutionFileEntity> files) {
        return filesOf(files, ExecutionFileDirection.OUTPUT);
    }

    private List<ExecutionFileDTO> filesOf(List<ExecutionFileEntity> files, ExecutionFileDirection direction) {
        return fileMapper.entitiesToDtos(files.stream().filter(file -> file.getDirection() == direction).toList());
    }

    private <T extends ExecutionMessageDTO> List<T> payloadsOf(List<ExecutionMessageEntity> messages, RunMessageTypes type, Class<T> payloadType) {
        return messages.stream()
                .filter(message -> message.getType() == type)
                .map(BaseExecutionMessageEntity::getPayload)
                .filter(payloadType::isInstance)
                .map(payloadType::cast)
                .toList();
    }

    private void withAppVersion(FederatedAppVersionEntity version, ExecutionRunDetailDTO dto) {
        dto.setFederatedAppId(version.getFederatedApp().getId());
        dto.setFederatedAppVersionId(version.getId());
        dto.setFederatedAppVersionName(version.getVersionFormatted());
    }
}
