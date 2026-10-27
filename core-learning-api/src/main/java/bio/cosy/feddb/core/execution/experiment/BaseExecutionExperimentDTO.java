package bio.cosy.feddb.core.execution.experiment;

import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.core.execution.config.ExecutionDiagramConfigDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class BaseExecutionExperimentDTO extends BaseDTO {
    private String name;
    private String description;
    private List<ExecutionDiagramConfigDTO> diagramConfigs = new ArrayList<>();
}
