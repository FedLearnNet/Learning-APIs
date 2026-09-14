package bio.cosy.feddb.core.api.model;

import bio.cosy.feddb.core.api.project.ProjectDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import lombok.Data;


@Data
public class FederatedModelSubDetailDataDTO {

    private ProjectDTO project;
    private QueryDTO query;
}

