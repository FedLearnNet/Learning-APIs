package bio.cosy.feddb.local.agent.text;

import bio.cosy.feddb.local.agent.embedding.SapBERTEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/** An unnamed column, e.g. an empty one Excel left behind a table, must not cost the other columns their scores. */
@ExtendWith(MockitoExtension.class)
class SapbertSimilartityTest {

    @Mock
    SapBERTEmbeddingModel sapBERT;

    @InjectMocks
    SapbertSimilartity similarity;

    @Test
    void scoresTheNamedColumnsWhenOneHasNoName() {
        when(sapBERT.enabled()).thenReturn(true);
        when(sapBERT.embedAll(anyList())).thenAnswer(invocation -> invocation.<List<String>>getArgument(0).stream()
                .map(text -> Embedding.from(new float[]{1, 0}))
                .toList());

        double[][] scores = similarity.semanticScores(List.of("age", "", "gender"), List.of("age at diagnosis"));

        assertEquals(1, scores[0][0], 1e-6);
        assertEquals(0, scores[0][1]);
        assertEquals(1, scores[0][2], 1e-6);
    }

    @Test
    void listsTheCandidatesClosestToEveryTextBestFirst() {
        when(sapBERT.enabled()).thenReturn(true);
        when(sapBERT.embedAll(anyList())).thenAnswer(invocation -> invocation.<List<String>>getArgument(0).stream()
                .map(text -> Embedding.from(text.toLowerCase().contains("carcinoma") ? new float[]{1, 0}
                        : text.toLowerCase().contains("lymphoma") ? new float[]{0, 1} : new float[]{1, 1}))
                .toList());

        List<List<String>> closest = similarity.closest(List.of("renal carcinoma", "lymphoma"),
                List.of("Lymphoma, NOS", "Adenocarcinoma, NOS", "Not Reported"), 2);

        assertEquals(List.of(List.of("Adenocarcinoma, NOS", "Not Reported"), List.of("Lymphoma, NOS", "Not Reported")), closest);
    }
}
