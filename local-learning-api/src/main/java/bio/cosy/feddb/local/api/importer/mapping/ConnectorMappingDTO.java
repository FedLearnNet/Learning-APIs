package bio.cosy.feddb.local.api.importer.mapping;

import lombok.Data;

@Data
public class ConnectorMappingDTO {
    private String column;
    private String mapping;
    private Long schemaId;
    private String visitTimestampMapping;
    private String visitIdMapping;
    private String timestampFormat;

    private ConnectorValueMappingConfigDTO valueMappingConfig;
}
