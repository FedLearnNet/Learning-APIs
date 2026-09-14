package bio.cosy.feddb.core.api.model.chat;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
public class ToolDTO {
    private String id;
    private String name;
    private boolean started;
    private boolean done;
    private List<String> content;
    private Object input;

    public ToolDTO(String name) {
        this.id = UUID.randomUUID().toString();
        this.started = true;
        this.name = name;
    }

    public void setStop() {
        this.started = false;
        this.done = true;
    }

    public void addContent(String content) {
        if (this.content == null) {
            this.content = new ArrayList<>();
        }
        this.content.add(content);
    }

    public void addContent(List<String> content) {
        if (this.content == null) {
            this.content = new ArrayList<>();
        }
        this.content.addAll(content);
    }

}
