package de.unihamburg.daibetes.api.project.experiment.federated.step;

import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepDTO;
import jakarta.persistence.Column;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProjectFederatedExperimentStepDTO extends BaseWorkflowStepDTO {

    private String channel;
    private String relayKey;

    private List<RunMessageMetricDTO> metrics;
}
