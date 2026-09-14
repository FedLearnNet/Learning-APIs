package bio.cosy.feddb.core.api.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;


@EqualsAndHashCode(callSuper = true)
@Data
public class ModelDetailDTO extends ModelDTO {

    private boolean createdByUser = false;

    private ModelAccessDTO creator;

    //Content relay of user access rights
    private List<ModelVersionDTO> modelVersions;

    //Only if user is owner
    private List<ModelAccessDTO> accesses;
}

