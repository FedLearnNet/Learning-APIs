package de.unihamburg.daibetes.execution.run;

import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentDTO;
import de.unihamburg.daibetes.execution.federation.ExecutionParticipantDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;


@EqualsAndHashCode(callSuper = true)
@Data
public class ExecutionRunDetailDTO extends ExecutionRunDTO {
    // The app version of a run of a single app.
    private Long federatedAppId;
    private Long federatedAppVersionId;
    private String federatedAppVersionName;
    // The experiment the run belongs to, if any.
    private ExecutionExperimentDTO experiment;
    // Who takes part in a federated run.
    private List<ExecutionParticipantDTO> participants = new ArrayList<>();
}
