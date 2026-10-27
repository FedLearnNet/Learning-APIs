package de.unihamburg.daibetes.execution.experiment;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.execution.experiment.BaseExecutionExperimentDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionExperimentDTO extends BaseExecutionExperimentDTO {
    private String keycloakId;
    private boolean test;
    private Long federatedAppId;
    private Long federatedAppVersionId;
    private String federatedAppVersionName;
    private Long projectId;
    // For a training across clinics.
    private String globalUniqueId;
    private ProjectStatus status;
    private ProjectDetailDTO projectVersion;
    private Long workflowId;
    private Long acceptanceCount;
    private Long acceptanceClinicCount;
    private String relayServerAddress;
    private Boolean modelCanBePublic;
    private Boolean modelNeedToBePublic;
}
