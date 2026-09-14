package de.unihamburg.daibetes.api.project.experiment.federated;

import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProjectFederatedExperimentDetailDTO extends ProjectFederatedExperimentDTO {

    private List<ProjectFederatedExperimentParticipantDTO> participants;
    private List<ProjectFederatedExperimentStepDTO> steps;
}
