package de.unihamburg.daibetes.services.research;

import de.unihamburg.daibetes.services.research.semanticscholar.SemanticScholarPaperDTO;
import de.unihamburg.daibetes.services.research.semanticscholar.SemanticScholarResponseDTO;
import de.unihamburg.daibetes.services.research.semanticscholar.SemanticScholarServiceImpl;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

//If other research services are added, they can be injected and their results combined here. like pubmed
@ApplicationScoped
public class ResearchService {

    @Inject
    SemanticScholarServiceImpl semanticScholarService;

    public List<SemanticScholarPaperDTO> search(String query) {
        try {
            SemanticScholarResponseDTO semanticResult = semanticScholarService.executeQuery(query);
            return semanticResult.getData();
        } catch (Exception e) {
            Log.errorf("Error during research service query: %s", e.getMessage());
            return List.of();
        }
    }
}
