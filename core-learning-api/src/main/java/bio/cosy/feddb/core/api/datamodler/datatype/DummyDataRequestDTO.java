package bio.cosy.feddb.core.api.datamodler.datatype;

import bio.cosy.feddb.core.api.project.PatientExportFeatureDTO;
import bio.cosy.feddb.core.api.project.SelectedDataIdsDTO;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DummyDataRequestDTO {

    @NotEmpty
    @Schema(description = "Selected ontology/datatype combinations")
    private List<PatientExportFeatureDTO> features;

    @Schema(defaultValue = "25")
    private int amount = 25;

    @Schema(defaultValue = "false")
    private boolean wideFormat = false;

    @Schema(defaultValue = "false")
    private boolean asFile = false;
}
