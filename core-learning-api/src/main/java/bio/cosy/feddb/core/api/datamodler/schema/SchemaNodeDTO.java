package bio.cosy.feddb.core.api.datamodler.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import bio.cosy.feddb.core.api.datamodler.BaseNodeDTO;
import bio.cosy.feddb.core.api.datamodler.Neo4jNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Neo4jNode(label = "Schema")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchemaNodeDTO extends BaseNodeDTO {
    private boolean isRoot = false;

    @NotBlank(message = "Name must not be blank")
    private String name;
    @NotBlank(message = "Description must not be blank")
    private String description;

    @NotNull(message = "Type must not be null")
    private SchemaNodeType type;

    private UUID parentId;
    private List<UUID> childrenIds;

    private LocalDate createdAt;
    private LocalDate updatedAt;
    private int version;


    private UUID dataTypeId;
    private UUID ontologyId;

    //only root
    private List<String> subscriptions;
    private String createdBy;

    public static String nodeLabel() {
        return SchemaNodeDTO.label(SchemaNodeDTO.class);
    }

    public void setType(String type) {
        this.type = SchemaNodeType.valueOf(type);
    }

    public void setType(SchemaNodeType type) {
        this.type = type;
    }

}
