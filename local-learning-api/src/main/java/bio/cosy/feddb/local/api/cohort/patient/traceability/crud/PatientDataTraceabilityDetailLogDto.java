package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class PatientDataTraceabilityDetailLogDto extends PatientDataTraceabilityLogDto {
    List<PatientDataChangeLogDto> changes;

    public PatientDataTraceabilityDetailLogDto(TracabilityLog log) {
        super(log);
    }

    public PatientDataTraceabilityDetailLogDto(TracabilityLog log, List<PatientDataChangeLogDto> changes) {
        super(log);
        this.changes = changes;
    }
}
