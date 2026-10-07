package eval.agent.etl.transform;

import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import eval.agent.etl.transform.judge.TransformerWriterJudge;
import eval.agent.etl.transform.judge.TransformerWriterPrompt;
import eval.agent.etl.transform.judge.TransformerWriterReviews;
import eval.agent.etl.transform.judge.TransformerWriterVerdict;
import eval.agent.etl.transform.pipeline.TransformerWriterAssistant;
import eval.agent.etl.transform.pipeline.TransformerWriterImporter;
import eval.agent.etl.transform.pipeline.TransformerWriterUploader;
import eval.agent.etl.transform.pojo.TransformerWriterResult;
import eval.agent.etl.transform.pojo.TransformerWriterReview;
import eval.agent.etl.transform.pojo.TransformerWriterSuggestion;
import eval.agent.etl.transform.report.TransformerWriterReport;
import eval.agent.etl.transform.setting.TransformerWriterDataset;
import eval.agent.etl.transform.setting.TransformerWriterDatasets;
import eval.agent.etl.transform.setting.TransformerWriterEvaluationProfile;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@QuarkusTest
@TestProfile(TransformerWriterEvaluationProfile.class)
public class TransformerWriterEvaluationTest {

    @ConfigProperty(name = "quarkus.langchain4j.openai.chat-model.model-name")
    String model;

    @Inject
    TransformerWriterUploader uploader;

    @Inject
    TransformerWriterAssistant assistant;

    @Inject
    TransformerWriterImporter importer;

    @Inject
    TransformerWriterReviews reviews;

    @Inject
    TransformerWriterJudge judge;

    @Test
    void evaluateTransformerAssistant() throws IOException {
        Map<String, List<TransformerWriterResult>> results = new LinkedHashMap<>();
        for (TransformerWriterDataset dataset : TransformerWriterDatasets.DATASETS) {
            ConnectorDTO connector = uploader.upload(dataset);
            TransformerWriterSuggestion suggestion = assistant.suggest(connector, dataset.getPatientIdColumn());
            ConnectorRunDTO run = importer.importWith(connector.getId(), suggestion.getConnector());
            results.put(dataset.getName(), reviews.of(connector, suggestion.getNodes(), run).stream()
                    .map(review -> new TransformerWriterResult(dataset.getName(), review, judge(review)))
                    .toList());
            TransformerWriterReport.save(model, results);
        }
    }

    private TransformerWriterVerdict judge(TransformerWriterReview review) {
        try {
            return judge.judge(TransformerWriterPrompt.render(review));
        } catch (RuntimeException e) {
            Log.warnf(e, "[WRITER-EVAL] The LLM judge failed on %s", review.getSchemaNode());
            return null;
        }
    }
}
