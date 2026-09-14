package bio.cosy.feddb.local.api.search;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;

@Data
public class SearchResultDTO<T extends BaseDTO> {
    private SearchResultType type;
    private T result;
    private String title;
    private int score;
}
