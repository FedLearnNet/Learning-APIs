package bio.cosy.feddb.core.api.query;

import lombok.Data;

/**
 * Data Transfer Object (DTO) for representing a query operator.
 * Sending of a query from global user to local clients
 * The actual operator containing information about the filter applied
 */
@Data
public class QueryOperatorDTO {
    // The operator, like ">", ">=", "==", etc., see validation.go for allowed values
    private QueryOperatorTypes operator;
    // The value can be string, or []string //TODO
    private String value;
}
