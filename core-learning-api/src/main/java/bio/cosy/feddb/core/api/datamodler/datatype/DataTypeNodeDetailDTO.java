package bio.cosy.feddb.core.api.datamodler.datatype;

import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class DataTypeNodeDetailDTO extends DataTypeNodeDTO {
    private List<OntologyNodeDTO> ontologies;
}
