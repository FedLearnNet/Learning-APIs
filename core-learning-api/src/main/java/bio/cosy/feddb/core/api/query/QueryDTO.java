package bio.cosy.feddb.core.api.query;

import bio.cosy.feddb.core.base.BaseAuthDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * Data Transfer Object (DTO) for representing a query operator.
 * Contains the query as well as the corresponding ID. used finally on the DB
 * after transformations by the local client
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class QueryDTO extends BaseAuthDTO {
    private String groupId;
    private String globalUniqueId;

    private Lang2QueryNodeDTO query;

    private String description;
    private String name;
    private Integer result;
    private String error;
    private boolean hasResult;
    private boolean hasFired;

    private UUID latestDataStatisticsRequest;
    private Date latestDataStatisticsRequestTimestamp;

    private Set<Long> projectIds;

    private Set<String> roles;

    @Override
    public String toString() {
        return "QueryDTO{" +
                "projectIds=" + projectIds +
                ", description='" + description + '\'' +
                ", query=" + query +
                ", name='" + name + '\'' +
                '}';
    }
}
