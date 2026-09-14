package bio.cosy.feddb.core.api.datamodler.ontology;

import bio.cosy.feddb.core.api.datamodler.BaseEdgeDTO;
import bio.cosy.feddb.core.api.datamodler.Neo4jEdge;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Neo4jEdge(label = "ONTOLOGY_REL")
public class OntologyEdgeDTO extends BaseEdgeDTO {

    private String type;
    private String description;
    private String rel;
    private String rela;
    private String sab;
}
