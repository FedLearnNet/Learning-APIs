package bio.cosy.feddb.local.api.importer.validation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConnectorValidationBulkRequestDTO {
    private Long schemaId;
    private String mapping;
    private Object value;
    private List<Object> values;

    public List<Object> resolvedValues() {
        if (values == null) {
            return java.util.Collections.singletonList(value);
        }

        List<Object> resolved = new ArrayList<>(values.size() + 1);
        if (value != null) {
            resolved.add(value);
        }
        resolved.addAll(values);
        return resolved;
    }
}
