package bio.cosy.feddb.core.api.project;

import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class PatientExportFilterDTO {
    private List<String> externalPatientIds;
    @Positive
    private Integer limit;
}
