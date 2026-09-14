package de.unihamburg.daibetes.api.runs.test;

import bio.cosy.feddb.core.api.run.RunMetaDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseAuthDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;

@EqualsAndHashCode(callSuper = true)
@Data
public class TestRunDTO extends BaseDTO {

    private RunStatusTypes status;
    private String error;

    private LinkedHashMap<String, Object> hyperParams;
    private LinkedHashMap<String, Object> inputData;
    private LinkedHashMap<String, String> inputFilePaths;
    private LinkedHashMap<String, Object> outputData;

    // Run metadata (timings today). RUNTIME in meta.timings always present when measured; OVERHEAD_*
    // only when posymed.runtime.overhead.enabled is true (stripped before exposure otherwise).
    private RunMetaDTO meta;

    private Long federatedAppId;
    private Long federatedAppVersionId;
    private String federatedAppVersionName;
}
