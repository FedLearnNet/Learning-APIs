package bio.cosy.feddb.local.api.cohort.patient.dataentry.metadata;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class MetaPatientDataEntryDTO extends BaseDTO {
    private Long schemaNodeId;
    private Object value;
}
// TODO: the whole metaEntry is not implemented yet
