package de.unihamburg.daibetes.api.runs.experiment.run;

import bio.cosy.feddb.core.api.run.RunMetaDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;

@EqualsAndHashCode(callSuper = true)
@Data
public class ExperimentRunDTO extends BaseDTO {

    private RunStatusTypes status;
    private String error;
    private String name;

    private String color;

    private LinkedHashMap<String, Object> hyperParams;
    private LinkedHashMap<String, Object> outputData;

    // Run metadata (timings today). RUNTIME in meta.timings always present when measured; OVERHEAD_*
    // only when posymed.runtime.overhead.enabled is true (stripped before exposure otherwise).
    private RunMetaDTO meta;

    private Long experimentId;

}
