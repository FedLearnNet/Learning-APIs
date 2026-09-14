package bio.cosy.feddb.core.api.model;

import bio.cosy.feddb.core.base.BaseAuthDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;


@EqualsAndHashCode(callSuper = true)
@Data
public class ModelAccessDTO extends BaseAuthDTO {

    @NotNull(message = "Model sub status is mandatory")
    private ModelAccess access;

    @NotNull(message = "Model id is mandatory")
    private Long modelId;

    private String groupName;

}

