package bio.cosy.feddb.core.api.datamodler.ontology;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OntologySearchResponseDTO {
    private OntologyNodeDTO node;
    private Double score;

    public OntologySearchResponseDTO(OntologyNodeDTO node) {
        this.node = node;
        this.score = (double) 1L;
    }
}
