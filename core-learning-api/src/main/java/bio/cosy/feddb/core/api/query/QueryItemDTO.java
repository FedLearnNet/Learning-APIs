package bio.cosy.feddb.core.api.query;

import lombok.Data;

import java.util.List;

/**
 * Data Transfer Object (DTO) for representing a query operator.
 * Combines the operator object, defining the exact filters, with the ontology ID to be filtered
 */
@Data
public class QueryItemDTO {
    // The ID of the ontology
    private String ontologyId;
    private String dataTypeId;
    // The operator object
    private List<QueryOperatorDTO> operator;
}
