package bio.cosy.feddb.local.api.importer.transformer.code;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeTransformerUsageDTO {
    private Long connectorId;
    private String connectorName;
    private Long cohortId;
}
