package bio.cosy.feddb.local.api.cohort.patient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Identifies a patient without loading any of their data. Used to populate pickers, such as the
 * patient filter of the cohort export.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientReferenceDTO {
    private Long id;
    private String externalPatientId;
}
