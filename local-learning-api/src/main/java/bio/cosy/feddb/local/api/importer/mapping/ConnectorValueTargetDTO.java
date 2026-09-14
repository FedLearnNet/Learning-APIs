package bio.cosy.feddb.local.api.importer.mapping;

import lombok.Data;

@Data
public class ConnectorValueTargetDTO {
    private String sourceValue;
    private String displayValue;
    private String value;
    private Long schemaId;
}
