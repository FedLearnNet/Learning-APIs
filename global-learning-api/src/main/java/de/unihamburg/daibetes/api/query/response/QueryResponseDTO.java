package de.unihamburg.daibetes.api.query.response;


import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.core.services.orch.dto.SystemComputingInfoReducedDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class QueryResponseDTO extends BaseDTO {

    private Long queryId;
    private String globalUniqueQueryId;
    private Integer count;
    private String error;
    private SystemComputingInfoReducedDTO system;
}
