package bio.cosy.feddb.core.api.datamodler.ontology;

import lombok.Data;

import java.util.List;

@Data
public class OntologyQueryAbilityDTO {
    private String ontologyId;
    private String dataTypeId;
    private List<String> clients;
}
