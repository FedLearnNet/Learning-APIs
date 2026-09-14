package de.unihamburg.daibetes.api.runs.experiment;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ExperimentDTO extends BaseDTO {

    private String name;
    private String description;

    private RunStatusTypes status;

    private String inputData;
    private LinkedHashMap<String, String> inputFilePaths;

    private Long federatedAppId;
    private Long federatedAppVersionId;
    private String federatedAppVersionName;

    private List<ExperimentDiagramConfigDTO> diagramConfigs;
}
