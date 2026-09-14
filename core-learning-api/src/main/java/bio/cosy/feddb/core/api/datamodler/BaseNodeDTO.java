package bio.cosy.feddb.core.api.datamodler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class BaseNodeDTO extends BaseDTO {

    private String label;

    public String label() {
        Neo4jNode ann = this.getClass().getAnnotation(Neo4jNode.class);
        if (ann == null || ann.label().isBlank()) {
            throw new IllegalStateException(
                    "DTO " + this.getClass().getName() +
                            " must be annotated with @Neo4jNode(label = \"...\")."
            );
        }
        return ann.label();
    }

    public static String label(Class<? extends BaseNodeDTO> clazz) {
        Neo4jNode ann = clazz.getAnnotation(Neo4jNode.class);
        if (ann == null || ann.label().isBlank()) {
            throw new IllegalStateException(
                    "DTO " + clazz.getName() +
                            " must be annotated with @Neo4jNode(label = \"...\")."
            );
        }
        return ann.label();
    }
}
