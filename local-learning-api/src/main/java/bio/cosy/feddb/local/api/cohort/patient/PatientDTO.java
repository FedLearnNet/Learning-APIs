package bio.cosy.feddb.local.api.cohort.patient;

import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryDTO;

import java.time.LocalDateTime;
import java.util.Set;

import bio.cosy.feddb.core.base.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

// All data considering one patient, timestamp, visit
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class PatientDTO extends BaseDTO {
    @NotNull
    private Long cohortId;
    @NotBlank
    @NotNull
    private String externalPatientId;
        // Either of these IDs must be provided
    private Set<PatientDataEntryDTO> dataEntries;
    private Long dataEntriesVersion;
    private LocalDateTime lastQueriedAt;
}
