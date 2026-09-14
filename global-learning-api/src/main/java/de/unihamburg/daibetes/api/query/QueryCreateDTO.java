package de.unihamburg.daibetes.api.query;

import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.base.CreateDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Data Transfer Object (DTO) for representing a query operator.
 * Contains the query as well as the corresponding ID. used finally on the DB
 * after transformations by the local client
 */
@EqualsAndHashCode(callSuper = true)
@Data
@CreateDTO
public class QueryCreateDTO extends QueryDTO {
}
