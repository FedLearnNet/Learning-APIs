package bio.cosy.feddb.core.api.datamodler.datatype;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataTypeSchemaEdgeDTO {
    private String schemaId;
    private String dataTypeId;
}
