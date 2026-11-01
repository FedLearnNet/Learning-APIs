package de.unihamburg.daibetes.execution.run;

import de.unihamburg.daibetes.api.development.engine.TestEngineMode;
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
    // The ENGINE run whose container served the run; none for the developer's local engine.
    private Long testEngineId;
    private TestEngineMode testEngineMode;
    // What the container of an ENGINE run is for.
    private TestEngineMode engineMode;
    // Who takes part in a federated run.
    private List<ExecutionParticipantDTO> participants = new ArrayList<>();
}
