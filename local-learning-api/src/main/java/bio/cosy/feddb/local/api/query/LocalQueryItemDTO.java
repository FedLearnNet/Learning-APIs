package bio.cosy.feddb.local.api.query;

import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;


@EqualsAndHashCode(callSuper = true)
@Data
public class LocalQueryItemDTO extends QueryItemDTO {
    private List<LocalSchemaNodeDTO> schemaNodes;
}
