package bio.cosy.feddb.core.api.model.workflow.chat;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class UiActionDTO {
    private UiActionType action;
    private UiActionKind kind;

    private Map<String, Object> params = new HashMap<>();

    private boolean autorun;

    private String label;

    //modelId, fileId e.g.
    private Long relatedId;

    public void setKind(String kind) {
        try {
            this.kind = UiActionKind.valueOf(kind);
        } catch (Exception e) {
            this.kind = UiActionKind.APP;
        }
    }
}
