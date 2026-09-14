package bio.cosy.feddb.core.api.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class EnhancedQueryItemDTO extends QueryItemDTO {
    private String ontologyName;
    private String dataTypeName;
}
