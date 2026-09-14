package de.unihamburg.daibetes.api.project.experiment.federated.participants;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProjectFederatedExperimentParticipantDTO extends BaseDTO {

    private String uniqueRandomClinicId;

    private RunStatusTypes projectStatus;

    private String currentNodeId;

    private ProjectStatus stepStatus;

    private Long experimentId;
    private Boolean modelCanBePublic;
    private Boolean isCoordinator;
}
