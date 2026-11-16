package de.unihamburg.daibetes.api.project.training.result;

import bio.cosy.feddb.core.api.socket.RunResultRequestType;
import bio.cosy.feddb.core.base.BaseDTO;
import de.unihamburg.daibetes.api.project.training.result.response.RunResultResponseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
public class RunResultRequestDTO extends BaseDTO {
    private UUID globalRequestId;
    private String requestKeycloakId;
    private RunResultRequestType type;
    private Long experimentId;
    private Long projectId;
    private int responsesReceived;
    private int totalParticipants;

    private List<RunResultResponseDTO> responses;
}
