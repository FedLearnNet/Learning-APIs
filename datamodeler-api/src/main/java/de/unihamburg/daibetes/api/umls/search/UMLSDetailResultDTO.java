package de.unihamburg.daibetes.api.umls.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UMLSDetailResultDTO {
    private String classType;
    private boolean suppressible;
    private boolean obsolete;
    private int cVMemberCount;
    private int atomCount;

    private String ui;
    private String name;
    private String rootSource;
    // urls
    private String parents;
    private String children;
    private String relations;
    private String attributes;
    private String ancestors;
    private String concepts;
    private String defaultPreferredAtom;
}
