package de.unihamburg.daibetes.services.research.semanticscholar;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class SemanticScholarPaperDTO {
    private String paperId;
    private Integer corpusId;

    private String url;

    private String title;

    @JsonProperty("abstract")
    private String abstractText;

    private String venue;
    private Integer referenceCount;
    private Integer citationCount;
    private Boolean isOpenAccess;
    private SemanticScholarOpenAccessPDFDTO openAccessPdf;
    private Integer year;

    private SemanticScholarExternalDTO externalIds;
    private SemanticScholarJournalDTO journal;
    private List<SemanticScholarAuthorDTO> authors;
}
