package bio.cosy.feddb.local.api.importer.load;

import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryDTO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConnectorLoadRow {
    @NotNull
    private List<ConnectorValidationResultDTO> validationResult = new ArrayList<>();
    @NotNull
    private List<PatientDataEntryDTO> entries = new ArrayList<>();
}
