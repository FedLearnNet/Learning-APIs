package eval.agent.etl.gdc.pipeline;

import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import eval.agent.etl.gdc.judge.GdcJudge;
import eval.agent.etl.gdc.judge.GdcJudgement;
import eval.agent.etl.gdc.pojo.GdcFieldResult;
import eval.agent.etl.gdc.pojo.GdcVerdict;
import eval.agent.etl.gdc.report.GdcReport;
import eval.agent.etl.gdc.score.GdcScoring;
import eval.agent.etl.gdc.setting.GdcStudy;
import eval.agent.etl.gdc.setting.MatchingBenchmark;
import eval.agent.etl.transform.judge.TransformerWriterReviews;
import eval.agent.etl.transform.pipeline.TransformerWriterAssistant;
import eval.agent.etl.transform.pipeline.TransformerWriterUploader;
import eval.agent.etl.transform.pojo.TransformerWriterReview;
import eval.agent.etl.transform.pojo.TransformerWriterSuggestion;
import eval.agent.etl.transform.setting.EvaluationRun;
import eval.agent.etl.transform.setting.TransformerWriterDataset;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps the studies of a schema matching benchmark onto its schema like a user of the assistant and scores the fields
 * against the ground truth; a study with results is skipped, so a stopped run continues.
 */
@ApplicationScoped
public class MatchingBenchmarkRunner {

    @ConfigProperty(name = "quarkus.langchain4j.openai.chat-model.model-name")
    String model;

    @Inject
    TransformerWriterUploader uploader;

    @Inject
    TransformerWriterAssistant assistant;

    @Inject
    GdcJudge judge;

    @Inject
    TransformerWriterReviews reviews;

    public void run(MatchingBenchmark benchmark, String schemaId, List<GdcStudy> studies) throws IOException {
        Path directory = EvaluationRun.directory(model);
        Map<String, List<GdcFieldResult>> results = EvaluationRun.load(directory.resolve(benchmark.resultsFile()),
                new TypeReference<LinkedHashMap<String, List<GdcFieldResult>>>() {
                }, new LinkedHashMap<>());
        for (GdcStudy study : studies) {
            if (results.containsKey(study.getName())) {
                continue;
            }
            TransformerWriterDataset dataset = new TransformerWriterDataset(schemaId, study.getName(), study.dataPath(),
                    ",", study.getCaseIdColumn(), null);
            ConnectorDTO connector = uploader.upload(dataset);
            TransformerWriterSuggestion suggestion = assistant.suggest(connector, study.getCaseIdColumn());
            EvaluationRun.saveEffort(directory, benchmark.getFile(), study.getName(), suggestion.getEffort());
            List<GdcFieldResult> fields = GdcScoring.score(benchmark, study, suggestion.getNodes(),
                    suggestion.getConnector().getTransformer());
            Map<String, TransformerWriterReview> reviewed = reviews.withoutImport(connector, suggestion.getNodes()).stream()
                    .collect(Collectors.toMap(review -> GdcScoring.field(review.getSchemaNode(), benchmark.isQualified()),
                            review -> review, (first, second) -> first));
            fields.forEach(field -> field.setReview(reviewed.get(field.getField())));
            fields.stream().filter(field -> field.getVerdict() == GdcVerdict.EXTRA).forEach(field -> judge(benchmark, study, field));
            results.put(study.getName(), fields);
            GdcReport.save(directory, EvaluationRun.label(model), benchmark, results);
        }
        if (studies.stream().allMatch(study -> results.containsKey(study.getName()))) {
            EvaluationRun.markComplete(directory, benchmark.getFile());
        }
    }

    // The ground truth names only some right mappings, so the judge tells the extras that are right all the same
    private void judge(MatchingBenchmark benchmark, GdcStudy study, GdcFieldResult field) {
        try {
            GdcJudgement judgement = judge.judge(benchmark.getSchema(), field.getField(),
                    GdcScoring.examples(study, field.getMapped()), field.getConfiguration());
            field.setJudged(judgement.isCorrect());
            field.setJudgeReason(judgement.getReason());
        } catch (RuntimeException e) {
            Log.warnf(e, "Judging %s of %s failed", field.getField(), study.getName());
        }
    }
}
