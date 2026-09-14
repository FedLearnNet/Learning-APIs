package de.unihamburg.daibetes.api.runs.experiment;

import de.unihamburg.daibetes.api.runs.experiment.run.ExperimentRunDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ExperimentDetailDTO extends ExperimentDTO {

    private List<ExperimentRunDTO> runs;
}
