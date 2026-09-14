package bio.cosy.feddb.core.api.store.graph;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.model.ModelDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolGraphNodeDTO {
    private FederatedAppDetailDTO app;
    private List<ModelDTO> models = new ArrayList<>();

    public ToolGraphNodeDTO(FederatedAppDetailDTO app) {
        this.app = app;
    }
}
