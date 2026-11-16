package bio.cosy.feddb.local.api.importer.mapping;

import lombok.Data;

import java.util.List;

@Data
public class ConnectorMappingDTO {
    private String column;
    private String mapping;
    private Long schemaId;
    private String visitTimestampMapping;
    private String visitIdMapping;
    private String timestampFormat;

    private ConnectorValueMappingConfigDTO valueMappingConfig;

    public static ConnectorMappingDTO getPatientIdMapping(String column, String externalPatientId) {
        ConnectorMappingDTO mapping = new ConnectorMappingDTO();
        mapping.setColumn(column);
        mapping.setMapping(externalPatientId);
        return mapping;
    }

    public static List<ConnectorMappingDTO> removePatientIdMapping(List<ConnectorMappingDTO> mappings, String externalPatientId) {
        return mappings.stream().filter(mapping -> !mapping.getMapping().equals(externalPatientId)).toList();
    }
}
