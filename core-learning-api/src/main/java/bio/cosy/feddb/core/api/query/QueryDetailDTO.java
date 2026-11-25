package bio.cosy.feddb.core.api.query;

import bio.cosy.feddb.core.api.query.item.EnhancedQueryItemDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class QueryDetailDTO extends QueryDTO {
    private List<EnhancedQueryItemDTO> enhancedQuery;
    private List<QueryDTO> olderQueries;
}
