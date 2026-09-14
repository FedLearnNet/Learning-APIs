package bio.cosy.feddb.core.api.datamodler.schema;

import bio.cosy.feddb.core.api.datamodler.BaseEdgeDTO;
import bio.cosy.feddb.core.api.datamodler.Neo4jEdge;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Neo4jEdge(label = "SCHEMA_REL")
public class SchemaEdgeDTO extends BaseEdgeDTO {

    public static String edgeLabel() {
        return SchemaEdgeDTO.label(SchemaEdgeDTO.class);
    }
}
