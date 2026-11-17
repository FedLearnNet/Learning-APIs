package bio.cosy.feddb.local.api.cohort;

import bio.cosy.feddb.local.api.cohort.inclusion.CohortCriterionDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UpdateCohortDTO {
    @NotBlank
    private String name;
    private String description;
    private String citeAs;
    private PublicationStatus status;
    private String purpose;
    private String copyright;
    private String copyrightLabel;
    private List<CohortCriterionDTO> criteria;
    // instead of a schema, a blank password keeps the stored one
    @Valid
    private ExternalDatabaseDTO externalDatabase;
}
