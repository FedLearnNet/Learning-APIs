package bio.cosy.feddb.core.execution.message;

import bio.cosy.feddb.core.base.BaseDTO;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type", //the enum: RunMessageTypes
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(
                value = ExecutionMessageLogDTO.class,
                name = "LOG" //based on the enum RunMessageTypes parsing
        ),
        @JsonSubTypes.Type(
                value = ExecutionMessageMetricDTO.class,
                name = "METRIC"
        ),
        @JsonSubTypes.Type(
                value = ExecutionMessageRoundDTO.class,
                name = "ROUND"
        )
})
public class ExecutionMessageDTO extends BaseDTO {

    private String process;
    private RunMessageTypes type;
    private String message;

    private Long runId;

    //for federated runs, the workerId is the participantId
    private String workerId;
}
