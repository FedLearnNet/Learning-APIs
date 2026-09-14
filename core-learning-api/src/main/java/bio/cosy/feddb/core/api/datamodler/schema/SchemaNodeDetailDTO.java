package bio.cosy.feddb.core.api.datamodler.schema;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class SchemaNodeDetailDTO extends SchemaNodeDTO {
    private OntologyNodeDTO ontology;
    private DataTypeNodeDTO dataType;
}
