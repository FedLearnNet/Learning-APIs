package bio.cosy.feddb.local.api.importer.mapping;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConnectorValueMappingConfigDTO {
    private ConnectorMappingMode mode;
    private String mappingColumn;
    private String valueColumn;
    private List<ConnectorValueTargetDTO> valueMappings = new ArrayList<>();
}
