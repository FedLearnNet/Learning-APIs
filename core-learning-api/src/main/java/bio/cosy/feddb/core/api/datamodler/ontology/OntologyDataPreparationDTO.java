package bio.cosy.feddb.core.api.datamodler.ontology;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeUsagesDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class OntologyDataPreparationDTO extends OntologyNodeDTO{
    private List<DataTypeUsagesDTO> datatypes;
}
