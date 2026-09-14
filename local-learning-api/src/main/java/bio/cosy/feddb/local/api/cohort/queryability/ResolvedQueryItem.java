package bio.cosy.feddb.local.api.cohort.queryability;

import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ResolvedQueryItem {
    private QueryItemDTO item;
    private List<SchemaNodeEntity> schemaNodes;
}
