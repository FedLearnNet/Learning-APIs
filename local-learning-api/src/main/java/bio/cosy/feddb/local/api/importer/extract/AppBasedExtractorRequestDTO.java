package bio.cosy.feddb.local.api.importer.extract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AppBasedExtractorRequestDTO", description = "DTO to trigger an app-based extractor run")
public class AppBasedExtractorRequestDTO {

    @Schema(
            description = "Cohort identifier used to resolve the target cohort file directory",
            required = true
    )
    @NotNull
    @Positive
    private Long cohortId;

    @Schema(
            description = "Container image of the app to execute",
            required = true
    )
    @NotNull
    @NotBlank
    private String appImage;

    @Schema(
            description = "Technical app version identifier",
            required = true
    )
    @NotNull
    @Positive
    private Integer appVersionId;

    @Schema(
            description = "Hyperparameters forwarded to the app execution"
    )
    private LinkedHashMap<String, Object> hyperParams = new LinkedHashMap<>();

    @Schema(
            description = "Input data forwarded to the app execution"
    )
    private Map<String, Object> inputData = new HashMap<>();
}
