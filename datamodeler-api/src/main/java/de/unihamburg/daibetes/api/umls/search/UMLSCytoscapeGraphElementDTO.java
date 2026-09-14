package de.unihamburg.daibetes.api.umls.search;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@ApplicationScoped
public class UMLSCytoscapeGraphElementDTO {
    private String id;
    private String name;
    private String source;
    private String target;
    private boolean inDB = false;

    public UMLSCytoscapeGraphElementDTO(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public UMLSCytoscapeGraphElementDTO(String id, String source, String target) {
        this.id = id;
        this.source = source;
        this.target = target;
    }
}
