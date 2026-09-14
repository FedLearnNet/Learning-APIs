package bio.cosy.feddb.core.api.datamodler.ontology;

import lombok.Data;

import java.util.List;

@Data
public class CreateOntologyDTO {
    private OntologyNodeDTO ontology;
    private List<OntologyEdgeDTO> edges;
}
