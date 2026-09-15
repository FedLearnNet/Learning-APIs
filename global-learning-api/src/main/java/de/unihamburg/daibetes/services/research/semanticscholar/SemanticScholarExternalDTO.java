package de.unihamburg.daibetes.services.research.semanticscholar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SemanticScholarExternalDTO {

    @JsonProperty("DOI")
    private String doi;

    @JsonProperty("CorpusId")
    private String corpusId;

    @JsonProperty("MAG")
    private String mag;

    @JsonProperty("PubMed")
    private String pubMed;
}
