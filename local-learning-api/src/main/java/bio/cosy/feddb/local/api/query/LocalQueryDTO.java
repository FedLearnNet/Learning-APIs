package bio.cosy.feddb.local.api.query;

import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
public class LocalQueryDTO extends BaseDTO {
    private String globalQueryId;
    private String keycloakId;
    private Set<Long> queriedCohortIds;
    private List<QueryItemDTO> query;
    private List<LocalQueryItemDTO> enhancedQuery;
    private String statusMessage;
    private QueryStatusEnum status = QueryStatusEnum.UNKNOWN;
    private LocalDateTime receivedAt;
}
