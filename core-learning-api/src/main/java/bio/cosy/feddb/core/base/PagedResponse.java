package bio.cosy.feddb.core.base;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {
    private List<T> results;
    private int page;
    private int pageSize;
    private long totalCount;

    public PagedResponse(List<T> results, int page, int pageSize) {
        this.results = results;
        this.page = page;
        this.pageSize = pageSize;
        this.totalCount = results.size();
    }

    public PagedResponse(int page, int pageSize) {
        this.results = new ArrayList<>();
        this.page = page;
        this.pageSize = pageSize;
        this.totalCount = 0;
    }

}

