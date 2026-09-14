package bio.cosy.feddb.local.api.importer.mapping;

import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryDTO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MappingRowResultDTO {
    private String externalPatientId;
    private List<ConnectorValidationResultDTO> validationResult;
    private List<PatientDataEntryDTO> entries = new ArrayList<>();

    public void addEntry(
            ConnectorMappingDTO mapping,
            Object value,
            String visitId,
            String visitTimestamp) {
        addEntry(mapping, value, visitId, visitTimestamp, false);
    }

    public void addEntry(
            ConnectorMappingDTO mapping,
            Object value,
            String visitId,
            String visitTimestamp,
            boolean isNullValue) {
        PatientDataEntryDTO entry = new PatientDataEntryDTO();
        entry.setSchemaNodeId(mapping.getSchemaId());
        entry.setValue(value);
        entry.setVisitId(visitId);
        entry.setVisitTimestamp(visitTimestamp);
        entry.setVisitTimestampFormat(mapping.getTimestampFormat());
        entry.setIsNullValue(isNullValue);
        this.entries.add(entry);
    }
}
