package bio.cosy.feddb.local.api.cohort.patient.dataentry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientDataEntryExportDTO {
    private Long id;
    private Long patientId;
    private String name;
    private String ontologyId;
    private String datatypeId;
    private Object value;
    private String visitId;
    private Instant visitTimestamp;
    private String visitTimestampFormat;
    private String importSchemaGroupId;
}
