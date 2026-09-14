package bio.cosy.feddb.local.api.importer.run.step;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ConnectorRunStepDTO extends BaseDTO {
    private List<RunMessageLogDTO> logs;
    private String lastLog;
    private String lastError;
    private RunStatusTypes status;

    private Float progress;

    private LinkedHashMap<String, Object> hyperParams;
    private LinkedHashMap<String, String> inputPaths;


    private String containerId;

    private Long connectorRunId;
    private Long transformationId;
}
