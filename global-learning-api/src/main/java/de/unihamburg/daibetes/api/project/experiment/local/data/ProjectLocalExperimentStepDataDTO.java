package de.unihamburg.daibetes.api.project.experiment.local.data;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProjectLocalExperimentStepDataDTO extends BaseDTO {

    //for v2
    private String result;
    private String name;

    private Long stepInputId;
    private Long stepOutputId;

    private FileDTO file;
}
