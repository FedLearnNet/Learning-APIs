package bio.cosy.feddb.local.api.schema.schemanode;

import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;


@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class LocalSchemaNodeWithPathDTO extends LocalSchemaNodeDTO {
    private String path;
}
