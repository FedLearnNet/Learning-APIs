package eval.agent.etl.gdc;

import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerContainerBO;
import bio.cosy.feddb.local.api.importer.transformer.session.AppTransformerSessionBO;
import com.fasterxml.jackson.core.type.TypeReference;
import eval.agent.etl.gdc.judge.GdcJudge;
import eval.agent.etl.gdc.judge.GdcJudgement;
import eval.agent.etl.gdc.pojo.GdcFieldResult;
import eval.agent.etl.gdc.pojo.GdcVerdict;
import eval.agent.etl.gdc.report.GdcReport;
import eval.agent.etl.gdc.score.GdcScoring;
import eval.agent.etl.gdc.setting.GdcStudies;
import eval.agent.etl.gdc.setting.GdcStudy;
import eval.agent.etl.transform.pipeline.TransformerWriterAssistant;
import eval.agent.etl.transform.pipeline.TransformerWriterUploader;
import eval.agent.etl.transform.pojo.TransformerWriterSuggestion;
import eval.agent.etl.transform.setting.EvaluationRun;
import eval.agent.etl.transform.setting.TransformerWriterDataset;
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

/**
 * Maps the studies of the GDC schema matching benchmark onto the GDC schema like a user of the assistant, and scores
 * the fields against the ground truth (see GdcReport). Needs the uploaded GDC schema (-Deval.gdc.schema-id) and the
 * study tables (download-gdc-sm.sh).
 */
@QuarkusTest
@TestProfile(TransformerWriterEvaluationProfile.class)
public class GdcBenchmarkEvaluationTest {

    @ConfigProperty(name = "quarkus.langchain4j.openai.chat-model.model-name")
    String model;

    @Inject
    TransformerWriterUploader uploader;

    @Inject
    TransformerWriterAssistant assistant;

    @Inject
    GdcJudge judge;

    @Inject
    CodeTransformerContainerBO codeRunner;

    @Inject
    AppTransformerSessionBO appSessions;

    @Test
    void matchTheStudiesOntoGdc() throws IOException {
        Path directory = EvaluationRun.directory(model);
        Map<String, List<GdcFieldResult>> results = EvaluationRun.load(directory.resolve(GdcReport.RESULTS_FILE),
                new TypeReference<LinkedHashMap<String, List<GdcFieldResult>>>() {
                }, new LinkedHashMap<>());
        for (GdcStudy study : GdcStudies.selected()) {
            if (results.containsKey(study.getName())) {
                continue;
            }
            TransformerWriterDataset dataset = new TransformerWriterDataset(GdcStudies.schemaId(), study.getName(),
                    study.dataPath(), ",", study.getCaseIdColumn(), null);
            ConnectorDTO connector = uploader.upload(dataset);
            TransformerWriterSuggestion suggestion = assistant.suggest(connector, study.getCaseIdColumn());
            List<GdcFieldResult> fields = GdcScoring.score(study, suggestion.getNodes(), suggestion.getConnector().getTransformer());
            fields.stream().filter(field -> field.getVerdict() == GdcVerdict.EXTRA).forEach(field -> judge(study, field));
            results.put(study.getName(), fields);
            GdcReport.save(directory, EvaluationRun.label(model), results);
        }
        if (GdcStudies.selected().stream().allMatch(study -> results.containsKey(study.getName()))) {
            EvaluationRun.markComplete(directory, "gdc-benchmark");
        }
    }

    // The ground truth names only some right mappings, so the judge tells the extras that are right all the same
    private void judge(GdcStudy study, GdcFieldResult field) {
        try {
            GdcJudgement judgement = judge.judge(field.getField(), GdcScoring.examples(study, field.getMapped()),
                    field.getConfiguration());
            field.setJudged(judgement.isCorrect());
            field.setJudgeReason(judgement.getReason());
        } catch (RuntimeException e) {
            Log.warnf(e, "Judging %s of %s failed", field.getField(), study.getName());
        }
    }

    @AfterEach
    void stopContainers() {
        codeRunner.stop();
        appSessions.closeAll();
    }
}
