package de.unihamburg.daibetes.service;

import de.unihamburg.daibetes.api.umls.search.UMLSSearchResultDTO;
import lombok.Data;

import java.util.List;

@Data
public class UMLSSearchResult {
    private String classType;
    private Long recCount;
    private List<UMLSSearchResultDTO> results;
}
