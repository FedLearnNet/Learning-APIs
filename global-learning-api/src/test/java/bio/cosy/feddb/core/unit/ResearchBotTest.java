package bio.cosy.feddb.core.unit;

import de.unihamburg.daibetes.agent.anlysis.bots.ResearchBot;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ResearchBotTest {

    @Inject
    ResearchBot researchBot;

    @Test
    void shouldInjectResearchBot() {
        assertNotNull(researchBot);
    }

    @Test
    void shouldReturnStructuredMarkdownAnswer() {
        String response = researchBot.answer(
                "test-session-1",
                "Find recent papers about federated learning in healthcare",
                "The user is interested in recent medical AI literature."
        );

        assertNotNull(response);
        assertFalse(response.isBlank());

        assertTrue(response.contains("Executive Summary"), "Missing Executive Summary section");
        assertTrue(response.contains("Key Papers"), "Missing Key Papers section");
        assertTrue(response.contains("Queries Run"), "Missing Queries Run section");
        assertTrue(response.contains("Confidence"), "Missing Confidence section");
    }

    @Test
    void shouldIncludeFullReferencesSection() {
        String response = researchBot.answer(
                "test-session-2",
                "Search for literature on graph neural networks in medicine",
                "Focus on biomedical applications."
        );

        assertNotNull(response);
        assertFalse(response.isBlank());

        assertTrue(response.contains("Full References"), "Missing Full References section");
    }

    @Test
    void shouldHandleEmptyContext() {
        String response = researchBot.answer(
                "test-session-3",
                "Find papers about multimodal learning in radiology",
                ""
        );

        assertNotNull(response);
        assertFalse(response.isBlank());

        assertTrue(response.contains("Queries Run"), "Expected executed queries to be listed");
    }

    @Test
    void shouldIncludeOncologyGuidanceForCancerTopics() {
        String response = researchBot.answer(
                "test-session-4",
                "Find recent papers on AI for breast cancer diagnosis",
                "The user is exploring oncology-related AI methods."
        );

        assertNotNull(response);
        assertFalse(response.isBlank());

        assertTrue(
                response.contains("I’m ready to help you with cancer-related information.")
                        || response.contains("I'm ready to help you with cancer-related information."),
                "Expected oncology guidance block for cancer-related input"
        );
    }

    @Test
    void shouldReturnSomeStructuredResponseForNicheTopic() {
        String response = researchBot.answer(
                "test-session-5",
                "Find papers about explainable AI for blood-brain barrier modeling",
                "Biomedical machine learning context."
        );

        assertNotNull(response);
        assertFalse(response.isBlank());

        assertTrue(response.contains("Executive Summary"));
        assertTrue(response.contains("Queries Run"));
    }

    @Test
    void shouldAlwaysListQueriesRun() {
        String response = researchBot.answer(
                "test-session-6",
                "Identify benchmark datasets for sepsis prediction using machine learning",
                "Clinical prediction and dataset discovery."
        );

        assertNotNull(response);
        assertFalse(response.isBlank());

        assertTrue(response.contains("Queries Run"), "Bot must always include executed queries");
    }
}
