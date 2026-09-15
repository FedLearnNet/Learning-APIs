package de.unihamburg.daibetes.services.research.semanticscholar;

import lombok.Data;

import java.util.List;

@Data
public class SemanticScholarResponseDTO {
    private String total;
    private String token;
    private List<SemanticScholarPaperDTO> data;
}
