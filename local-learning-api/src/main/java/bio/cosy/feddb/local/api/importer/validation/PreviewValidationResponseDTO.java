package bio.cosy.feddb.local.api.importer.validation;

import lombok.Data;

import java.util.List;

@Data
public class PreviewValidationResponseDTO {
    private String column;
    private List<PreviewValidationResponseElementDTO> checks;
    /** Non-blocking warnings about the reliability of this column's recomputed categories. */
    private List<PreviewValidationWarningDTO> warnings;
}
