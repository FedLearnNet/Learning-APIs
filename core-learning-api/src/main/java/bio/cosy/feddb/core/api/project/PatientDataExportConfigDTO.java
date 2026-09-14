package bio.cosy.feddb.core.api.project;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;

@Data
public class PatientDataExportConfigDTO {
    private boolean appBased;
    //for native export
    private boolean wideFormat;
    private EnumSet<PatientDataPivotJoinField> joinFields;
    private PatientDataPivotDuplicatePolicy duplicatePolicy;
    //For app based export
    private LinkedHashMap<String, Object> hyperParams;
    private Long globalAppVersionId;

    //For selection
    private List<PatientExportFeatureDTO> features;

    //Narrows the exported patients; null or empty means every patient of the cohort
    @Valid
    private PatientExportFilterDTO patientFilter;
}
