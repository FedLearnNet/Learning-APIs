package de.unihamburg.daibetes.api.umls.search;

import lombok.Data;

@Data
public class UMLSSearchResultDTO {
    private String ui;
    private String uri;
    private String name;
    private String rootSource;
}
