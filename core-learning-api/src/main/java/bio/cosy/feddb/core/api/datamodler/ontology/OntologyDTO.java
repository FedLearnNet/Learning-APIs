package bio.cosy.feddb.core.api.datamodler.ontology;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OntologyDTO {
    private List<OntologyEdgeDTO> edges;
    private List<OntologyNodeDTO> nodes;

    public OntologyDTO(List<OntologyEdgeDTO> edges, OntologyNodeDTO nodes) {
        this.edges = edges;
        this.nodes = List.of(nodes);
    }

    public OntologyDTO(OntologyNodeDTO nodes) {
        this.edges = List.of();
        this.nodes = List.of(nodes);
    }
}
