package bio.cosy.feddb.local.api.learning.project.run.data;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class FederatedLearningExperimentStepDataDTO extends BaseDTO {

    //for v2
    private String result;
    private String name;

    private Long stepInputId;
    private Long stepOutputId;

    private FileDTO file;
}
