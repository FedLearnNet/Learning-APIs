package bio.cosy.feddb.core.api.store.graph;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ToolGraphPathDTO {
    private Long fromAppId;
    private Long toAppId;

    private List<ToolGraphEdgeDTO> edges = new ArrayList<>();

    private int hops;
}
