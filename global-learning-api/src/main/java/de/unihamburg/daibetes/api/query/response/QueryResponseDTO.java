package de.unihamburg.daibetes.api.query.response;


import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.services.orch.dto.SystemComputingInfoReducedDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
public class QueryResponseDTO extends BaseDTO {

    private Integer count;
    private SystemComputingInfoReducedDTO system;
    private Set<ExecutionMode> supportedModes;
    private String clientName;
}
