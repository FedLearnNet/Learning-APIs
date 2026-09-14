package bio.cosy.feddb.local.api.cohort.patient.export;

import bio.cosy.feddb.core.api.project.PatientDataPivotJoinField;
import bio.cosy.feddb.core.api.project.PatientDataPivotDuplicatePolicy;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryExportDTO;
import lombok.*;

import java.time.Instant;
import java.util.EnumSet;

public class PatientDataPivot {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static final class Options {
        private EnumSet<PatientDataPivotJoinField> joinFields = EnumSet.of(PatientDataPivotJoinField.PATIENT_ID, PatientDataPivotJoinField.VISIT_ID);
        private PatientDataPivotDuplicatePolicy duplicatePolicy = PatientDataPivotDuplicatePolicy.ERROR;
    }

    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    public static final class GroupKey {
        private final Long patientId;
        private final String visitId;
        private final Instant visitTimestamp;
        private final String visitTimestampFormat;
        private final String importSchemaGroupId;

        static GroupKey of(PatientDataEntryExportDTO dto, EnumSet<PatientDataPivotJoinField> joinFields) {
            return new GroupKey(
                    joinFields.contains(PatientDataPivotJoinField.PATIENT_ID) ? dto.getPatientId() : null,
                    joinFields.contains(PatientDataPivotJoinField.VISIT_ID) ? dto.getVisitId() : null,
                    joinFields.contains(PatientDataPivotJoinField.VISIT_TIMESTAMP) ? dto.getVisitTimestamp() : null,
                    joinFields.contains(PatientDataPivotJoinField.VISIT_TIMESTAMP_FORMAT) ? dto.getVisitTimestampFormat() : null,
                    joinFields.contains(PatientDataPivotJoinField.IMPORT_SCHEMA_GROUP_ID) ? dto.getImportSchemaGroupId() : null
            );
        }

    }
}
