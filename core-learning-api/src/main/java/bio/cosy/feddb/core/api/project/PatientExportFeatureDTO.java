package bio.cosy.feddb.core.api.project;

import lombok.Data;

import java.util.List;

@Data
public class PatientExportFeatureDTO {
    private String name;
    private Integer order;
    private List<SelectedDataIdsDTO> allowedDataIds;
    private String targetDatatypeId;
}
