package bio.cosy.feddb.local.api.cohort;

import bio.cosy.feddb.core.base.BaseAuthDTO;
import bio.cosy.feddb.local.api.cohort.inclusion.CohortCriterionDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class CohortDTO extends BaseAuthDTO {

    @NotBlank
    private String name;

    private String description;

    private String citeAs;

    private PublicationStatus status;

    private LocalDate approvalDate;

    private String purpose;

    private String copyright;

    private String copyrightLabel;

    private List<CohortCriterionDTO> criteria;

    private int amountOfPatients;

    private Long internalSchemaId;

    private String globalSchemaId;

    private boolean deletionInProgress;
}
