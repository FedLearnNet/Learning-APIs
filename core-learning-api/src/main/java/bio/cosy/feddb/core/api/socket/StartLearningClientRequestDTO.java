package bio.cosy.feddb.core.api.socket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartLearningClientRequestDTO {
    private String globalUniqueLearningExperimentId;
    private String coordinatorId; // uniqueRandomClinicId
    private Boolean modelCanBePublic; // combined decision of all participating clinics
}
