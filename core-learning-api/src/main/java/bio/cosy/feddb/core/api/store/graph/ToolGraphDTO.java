package bio.cosy.feddb.core.api.store.graph;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ToolGraphDTO {
    private List<ToolGraphNodeDTO> nodes = new ArrayList<>();
    private List<ToolGraphEdgeDTO> edges = new ArrayList<>();

    public void addNode(ToolGraphNodeDTO node) {
        if(nodes == null) {
            nodes = new ArrayList<>();
        }
        this.nodes.add(node);
    }

    public void addNode(FederatedAppDetailDTO node) {
        addNode(new ToolGraphNodeDTO(node));
    }
}
