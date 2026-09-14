package de.unihamburg.daibetes.api.runs.experiment.run;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;

@EqualsAndHashCode(callSuper = true)
@Data
public class UpdateExperimentRunDTO extends ExperimentRunDTO {

    private LinkedHashMap<String, Object> inputData;
    private LinkedHashMap<String, Object> outputData;
    private LinkedHashMap<String, String> inputFilePaths;

}
