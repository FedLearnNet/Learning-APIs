package de.unihamburg.daibetes.api.project.experiment.federated.metrics.response;

import bio.cosy.feddb.core.api.socket.RunMetricDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class RunMetricsResponseDTO extends BaseDTO {
    private String randomClinicId;
    private Long requestId;
    private List<RunMetricDTO> metrics;
}
