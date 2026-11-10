package bio.cosy.feddb.core.api.datamodler.ontology;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OntologyDataTypeDTO {
    private OntologyNodeDTO ontology;
    private DataTypeNodeDTO dataType;
    /** Number of distinct subscriptions of the root schema(s) containing this pair. */
    private Integer subscriptionsCount;
}
