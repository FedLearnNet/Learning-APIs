package bio.cosy.feddb.core.api.datamodler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class BaseEdgeDTO extends BaseDTO {
    private String label;
    private String sourceId;
    private String targetId;

    public String label() {
        Neo4jEdge ann = this.getClass().getAnnotation(Neo4jEdge.class);
        if (ann == null || ann.label().isBlank()) {
            throw new IllegalStateException(
                    "DTO " + this.getClass().getName() +
                            " must be annotated with @Neo4jNode(label = \"...\")."
            );
        }
        return ann.label();
    }

    public static String label(Class<? extends BaseEdgeDTO> clazz) {
        Neo4jEdge ann = clazz.getAnnotation(Neo4jEdge.class);
        if (ann == null || ann.label().isBlank()) {
            throw new IllegalStateException(
                    "DTO " + clazz.getName() +
                            " must be annotated with @Neo4jNode(label = \"...\")."
            );
        }
        return ann.label();
    }
}
