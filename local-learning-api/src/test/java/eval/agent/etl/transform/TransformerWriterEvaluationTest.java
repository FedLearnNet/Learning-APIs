package eval.agent.etl.transform;

import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerContainerBO;
import bio.cosy.feddb.local.api.importer.transformer.session.AppTransformerSessionBO;
import com.fasterxml.jackson.core.type.TypeReference;
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
import eval.agent.etl.transform.setting.EvaluationRun;
import eval.agent.etl.transform.setting.TransformerWriterDataset;
import eval.agent.etl.transform.setting.TransformerWriterDatasets;
import eval.agent.etl.transform.setting.TransformerWriterTruth;
import eval.agent.etl.transform.setting.TransformerWriterEvaluationProfile;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
    TransformerWriterTruth truth;

    @Inject
    TransformerWriterJudge judge;

    @Inject
    CodeTransformerContainerBO codeRunner;

    @Inject
    AppTransformerSessionBO appSessions;

    @Test
    void evaluateTransformerAssistant() throws IOException {
        Path directory = EvaluationRun.directory(model);
        Map<String, List<TransformerWriterResult>> results = EvaluationRun.load(directory.resolve(TransformerWriterReport.RESULTS_FILE),
                new TypeReference<LinkedHashMap<String, List<TransformerWriterResult>>>() {
                }, new LinkedHashMap<>());
        // A dataset the LLM failed on, e.g. because its server crashed, is evaluated again
        results.values().removeIf(TransformerWriterEvaluationTest::failed);
        for (TransformerWriterDataset dataset : TransformerWriterDatasets.selected()) {
            if (results.containsKey(dataset.getName())) {
                continue;
            }
            ConnectorDTO connector = uploader.upload(dataset);
            TransformerWriterSuggestion suggestion = assistant.suggest(connector, dataset.getPatientIdColumn());
            EvaluationRun.saveEffort(directory, "transformer-writer", dataset.getName(), suggestion.getEffort());
            ConnectorRunDTO run = importer.importWith(connector.getId(), suggestion.getConnector());
            results.put(dataset.getName(), reviews.of(connector, suggestion.getNodes(), run, truth.values(dataset)).stream()
                    .map(review -> new TransformerWriterResult(dataset.getName(), review, judge(review)))
                    .toList());
            TransformerWriterReport.save(directory, EvaluationRun.label(model), results);
        }
        if (TransformerWriterDatasets.selected().stream().allMatch(dataset -> results.containsKey(dataset.getName()))
                && results.values().stream().noneMatch(TransformerWriterEvaluationTest::failed)) {
            EvaluationRun.markComplete(directory, "transformer-writer");
        }
        List<String> wrong = results.values().stream().flatMap(List::stream)
                .filter(result -> result.getReview().getTruthShift() != null
                        && result.getReview().getTruthShift() > TransformerWriterReport.CORRECT_SHIFT)
                .map(result -> result.getDataset() + " " + result.getReview().getSchemaNode())
                .toList();
        assertTrue(wrong.isEmpty(), "Every schema node has to store the values of the original file: " + wrong);
    }

    private static boolean failed(List<TransformerWriterResult> dataset) {
        return dataset.stream().anyMatch(result -> EvaluationRun.failed(result.getReview(), result.getVerdict()));
    }

    @AfterEach
    void stopContainers() {
        codeRunner.stop();
        appSessions.closeAll();
    }

    private TransformerWriterVerdict judge(TransformerWriterReview review) {
        try {
            return judge.judge(TransformerWriterPrompt.render(review));
        } catch (RuntimeException e) {
            Log.warnf(e, "[ETL-Eval:judge] Failed on %s", review.getSchemaNode());
            return null;
        }
    }
}
