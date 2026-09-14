package de.unihamburg.daibetes.api.umls.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UMLSAttributeResultDTO {
    private String classType;
    private String ui;
    private String rootSource;

    private String name;
    private String value;
}
