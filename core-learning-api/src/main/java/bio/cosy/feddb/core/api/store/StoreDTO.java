package bio.cosy.feddb.core.api.store;


import bio.cosy.feddb.core.api.app.FederatedAppDTO;
import bio.cosy.feddb.core.api.model.ModelDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoreDTO {
    private FederatedAppDTO app;
    private ModelDTO model;
    private WorkflowDTO workflow;

    public StoreDTO(FederatedAppDTO app) {
        this.app = app;
    }

    public StoreDTO(ModelDTO model) {
        this.model = model;
        this.app = model.getFederatedApp();
    }

    public StoreDTO(WorkflowDTO workflow) {
        this.workflow = workflow;
    }

    @JsonIgnore
    public Long getId() {
        if (app != null) {
            return app.getId();
        } else if (model != null) {
            return model.getId();
        } else if (workflow != null) {
            return workflow.getId();
        } else {
            return null;
        }
    }
}
