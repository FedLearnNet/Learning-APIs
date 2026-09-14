package bio.cosy.feddb.core.api.store.graph;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ToolGraphEdgeDTO {
    private Long fromAppId;
    private Long toAppId;

    private List<ToolPortMatchDTO> matches = new ArrayList<>();

    private int score;
}
