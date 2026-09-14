package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.RevisionType;

@Data
@NoArgsConstructor
public class PatientDataChangeLogDto {
    private Integer revId;
    private RevisionType changeType;

    private Long schemaNodeId;
    private String propertyName;
    private Object previousData;
    private Object currentData;
}
