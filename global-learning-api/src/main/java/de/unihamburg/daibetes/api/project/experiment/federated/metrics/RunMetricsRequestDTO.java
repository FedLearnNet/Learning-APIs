package de.unihamburg.daibetes.api.project.experiment.federated.metrics;

import bio.cosy.feddb.core.base.BaseDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.metrics.response.RunMetricsResponseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
public class RunMetricsRequestDTO extends BaseDTO {
    private UUID globalRequestId;
    private String requestKeycloakId;
    private Long experimentId;
    private Long projectId;
    private int responsesReceived;
    private int totalParticipants;

    private List<RunMetricsResponseDTO> responses;
}
