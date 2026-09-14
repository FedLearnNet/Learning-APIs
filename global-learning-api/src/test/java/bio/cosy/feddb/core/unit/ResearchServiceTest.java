package bio.cosy.feddb.core.unit;

import de.unihamburg.daibetes.agent.anlysis.bots.ResearchBot;
import de.unihamburg.daibetes.services.research.ResearchService;
import de.unihamburg.daibetes.services.research.semanticscholar.SemanticScholarPaperDTO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ResearchServiceTest {

    @Inject
    ResearchService researchService;

    @Inject
    ResearchBot researchBot;


    @Test
    void shouldReturnNonNullListForValidQuery() {
        List<SemanticScholarPaperDTO> result = researchService.search("cancer biomarker");

        assertNotNull(result);
    }

    @Test
    void shouldReturnResultsForCommonScientificQuery() {
        List<SemanticScholarPaperDTO> result = researchService.search("machine learning in medicine");

        assertNotNull(result);
        assertFalse(result.isEmpty(), "Expected at least one paper for a common query");
    }

    @Test
    void shouldNotThrowForEmptyQuery() {
        List<SemanticScholarPaperDTO> result = assertDoesNotThrow(() ->
                researchService.search("")
        );

        assertNotNull(result);
    }

    @Test
    void shouldNotThrowForStrangeQuery() {
        List<SemanticScholarPaperDTO> result = assertDoesNotThrow(() ->
                researchService.search("!!!@@@###___unlikely_query___###@@@!!!")
        );

        assertNotNull(result);
    }
}
