package de.unihamburg.daibetes.api.umls.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UMLSAtomResultDTO {
    private String classType;
    private String ui;
    private boolean suppressible;
    private boolean obsolete;
    private String rootSource;
    private String termType;
    private String code;
    private String concept;
    private String sourceConcept;
    private String sourceDescriptor;
    private String attributes;
    private String parents;
    private String ancestors;
    private String children;
    private String descendants;
    private String relations;
    private String name;
    private String language;
}
