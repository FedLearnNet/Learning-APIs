package bio.cosy.feddb.local.api.importer.run.preview.cache;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConnectorPreviewTransformationCacheDTO extends BaseDTO {

    private Long connectorId;
    private Long transformerId;
    private int stepIndex;
    private String fingerprint;
    private List<Map<String, Object>> rows;
    private List<String> columns;
    private Integer rowCount;
}
