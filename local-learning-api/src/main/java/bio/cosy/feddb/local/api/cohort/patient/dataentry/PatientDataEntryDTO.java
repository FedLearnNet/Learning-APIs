package bio.cosy.feddb.local.api.cohort.patient.dataentry;

import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.metadata.MetaPatientDataEntryDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class PatientDataEntryDTO extends BaseDTO {
    @NotNull
    private Long schemaNodeId;
    private Object value;
    private String visitId;
    private String visitTimestamp;
    private String visitTimestampFormat;
    private Boolean isNullValue = false;
    private Set<MetaPatientDataEntryDTO> metaData; // JSON string representation of metadata
}
