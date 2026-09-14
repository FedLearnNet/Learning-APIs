package de.unihamburg.daibetes.api.umls.search;

import de.unihamburg.daibetes.api.umls.UMLSSources;
import lombok.Data;

@Data
public class UMLSIdSourceDTO {
    private String id;
    private UMLSSources source;
}
