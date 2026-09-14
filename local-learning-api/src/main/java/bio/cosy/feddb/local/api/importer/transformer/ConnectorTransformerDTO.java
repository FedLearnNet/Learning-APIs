package bio.cosy.feddb.local.api.importer.transformer;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConnectorTransformerDTO extends BaseDTO {

    private String moduleName;
    private String methodName;
    private Map<String, Object> inputMapping;
    private Map<String, Object> returnMapping;
    private String column;
    private Integer position;

    // For remote app execution
    private Long appVersionId;
    private String appImage;
    private LinkedHashMap<String, Object> hyperparams;

    private Long connectorId;
}
