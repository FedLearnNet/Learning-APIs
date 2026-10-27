package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptMapper;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;
import de.unihamburg.daibetes.execution.message.ExecutionMessageEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface ExecutionAttemptMapper extends BaseExecutionAttemptMapper<ExecutionAttemptDTO, ExecutionAttemptEntity> {

    @Mappings({
            @Mapping(target = "stepId", source = "step.id"),
            @Mapping(target = "stepPublicId", source = "step.publicId"),
            @Mapping(target = "runId", source = "step.run.id"),
            @Mapping(target = "runPublicId", source = "step.run.publicId"),
            @Mapping(target = "scoreMetric", source = "scoreMessage")
    })
    ExecutionAttemptDTO entityToDto(ExecutionAttemptEntity entity);

    // The score message is linked when the app reports the search metric, never taken over from a request.
    @Mappings({
            @Mapping(target = "step.id", source = "stepId"),
            @Mapping(target = "scoreMessage", ignore = true),
            @Mapping(target = "files", ignore = true),
            @Mapping(target = "participant", ignore = true),
            @Mapping(target = "currentRound", ignore = true),
            @Mapping(target = "messagesSent", ignore = true),
            @Mapping(target = "messagesReceived", ignore = true),
            @Mapping(target = "waitingFor", ignore = true)
    })
    ExecutionAttemptEntity dtoToEntity(ExecutionAttemptDTO dto);

    default ExecutionMessageMetricDTO scoreMetricOf(ExecutionMessageEntity message) {
        return message != null && message.getPayload() instanceof ExecutionMessageMetricDTO metric ? metric : null;
    }
}
