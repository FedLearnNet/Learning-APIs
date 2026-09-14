package bio.cosy.feddb.core.api.datamodler.datatype;

import bio.cosy.feddb.core.api.datamodler.BaseEdgeDTO;
import bio.cosy.feddb.core.api.datamodler.Neo4jEdge;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Neo4jEdge(label = "DATATYPE_REL")
public class DataTypeEdgeDTO extends BaseEdgeDTO {
}
