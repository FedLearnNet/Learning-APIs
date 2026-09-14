package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryEntity;
import lombok.Data;
import org.hibernate.envers.RevisionType;

@Data
public class TracabilityDataChangeLog {

    private PatientDataEntryEntity dataEntry;
    private Integer revisionId;
    private RevisionType changeType;
}
