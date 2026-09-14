package bio.cosy.feddb.core.api.project;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProjectDTO extends BaseDTO {


    @NotBlank(message = "Name is mandatory")
    private String name;

    @NotBlank(message = "Description is mandatory")
    private String description;

    private String globalUniqueQueryId;
    private Long queryId;
    private Long workflowId;

    private String role;

    private ProjectStatus status = ProjectStatus.INIT;

    private PatientDataExportConfigDTO exportConfig;

    //For the controller, that he can decide if the controller needs data
    private boolean coordinatorHasData = false;

    //IF not set, the platform will decide randomly which client is coordinator
    private boolean platformIsCoordinator = false;

    //People can upload one file tom project for experiments and coordinator data but dont need to
    private FileDTO file;
}
