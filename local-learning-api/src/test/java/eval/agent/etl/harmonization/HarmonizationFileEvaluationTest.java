package eval.agent.etl.harmonization;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.helper.ResourceLoader;
import bio.cosy.feddb.local.api.harmonization.HarmonizationBO;
import bio.cosy.feddb.local.api.harmonization.HarmonizationDTO;
import bio.cosy.feddb.local.api.harmonization.HarmonizationFileBO;
import bio.cosy.feddb.local.api.harmonization.HarmonizationResultDTO;
import bio.cosy.feddb.local.api.harmonization.HarmonizationRunBO;
import bio.cosy.feddb.local.api.harmonization.HarmonizationTargetDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerContainerBO;
import bio.cosy.feddb.local.api.importer.transformer.session.AppTransformerSessionBO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import eval.agent.etl.harmonization.judge.HarmonizationFileReviews;
import eval.agent.etl.harmonization.pipeline.HarmonizationFileAssistant;
import eval.agent.etl.harmonization.pojo.HarmonizationFileColumnResult;
import eval.agent.etl.harmonization.pojo.HarmonizationFileResult;
import eval.agent.etl.harmonization.report.HarmonizationFileReport;
import eval.agent.etl.transform.judge.TransformerWriterJudge;
import eval.agent.etl.transform.judge.TransformerWriterPrompt;
import eval.agent.etl.transform.judge.TransformerWriterVerdict;
import eval.agent.etl.transform.pojo.TransformerWriterReview;
import eval.agent.etl.transform.pojo.TransformerWriterSuggestion;
import eval.agent.etl.transform.report.TransformerWriterReport;
import eval.agent.etl.transform.setting.TransformerWriterDataset;
import eval.agent.etl.transform.setting.TransformerWriterDatasets;
import eval.agent.etl.transform.setting.TransformerWriterEvaluationProfile;
import eval.agent.etl.transform.setting.TransformerWriterTruth;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;


@QuarkusTest
@TestProfile(TransformerWriterEvaluationProfile.class)
public class HarmonizationFileEvaluationTest {

    private static final String REFERENCE_PATH = "/connectors/clinic_001.csv";
    private static final String REFERENCE_KEY = "patient_nbr";
    private static final String FULLY_MAPPABLE = "US 130";

    @ConfigProperty(name = "quarkus.langchain4j.openai.chat-model.model-name")
    String model;

    @Inject
    HarmonizationFileBO fileBO;

    @Inject
    HarmonizationBO harmonizationBO;

    @Inject
    HarmonizationRunBO runBO;

    @Inject
    HarmonizationFileAssistant assistant;

    @Inject
    HarmonizationFileReviews reviews;

    @Inject
    TransformerWriterJudge judge;

    @Inject
    FLNetClientConfig config;

    @Inject
    CodeTransformerContainerBO codeRunner;

    @Inject
    AppTransformerSessionBO appSessions;

    @Test
    void harmonizeOntoTheFirstClinic() throws IOException {
        String keycloakId = config.user().systemUserName();
        Long referenceId = upload(REFERENCE_PATH, keycloakId);
        List<HarmonizationTargetDTO> targets = transaction(() -> runBO.targets(referenceId, REFERENCE_KEY, keycloakId));
        List<ColumnProfile> reference = transaction(() -> fileBO.profiles(referenceId, keycloakId));
        targets.forEach(target -> Log.infof("[Harmonization-Eval:schema] %s (%s%s): %s", target.getName(),
                target.getDataType().getType(), target.isStaticColumn() ? ", static" : "", target.getDataType().getDescription()));

        List<HarmonizationFileResult> results = new ArrayList<>();
        for (TransformerWriterDataset dataset : TransformerWriterDatasets.selected()) {
            results.add(evaluate(dataset, referenceId, targets, reference, keycloakId));
            HarmonizationFileReport.save(model, REFERENCE_PATH, results);
        }

        List<String> incomplete = results.stream()
                .filter(result -> result.getDataset().startsWith(FULLY_MAPPABLE) && !result.getUnmapped().isEmpty())
                .map(result -> result.getDataset() + " " + result.getUnmapped())
                .toList();
        List<String> wrong = results.stream()
                .filter(result -> result.truthCorrect() < result.truthCompared())
                .map(result -> result.getDataset() + " " + result.getColumns().stream()
                        .filter(column -> column.getTruthShift() != null && column.getTruthShift() > TransformerWriterReport.CORRECT_SHIFT)
                        .map(HarmonizationFileColumnResult::getColumn).toList())
                .toList();
        assertTrue(incomplete.isEmpty() && wrong.isEmpty(), "Every column of the reference has to be mapped: " + incomplete
                + ", and every value has to be the one of the original file: " + wrong);
    }

    @AfterEach
    void stopContainers() {
        codeRunner.stop();
        appSessions.closeAll();
    }

    private HarmonizationFileResult evaluate(TransformerWriterDataset dataset, Long referenceId, List<HarmonizationTargetDTO> targets,
                                             List<ColumnProfile> reference, String keycloakId) {
        HarmonizationDTO harmonization = new HarmonizationDTO();
        harmonization.setName(dataset.getName());
        harmonization.setReferenceFileId(referenceId);
        harmonization.setReferenceKey(REFERENCE_KEY);
        harmonization.setTargets(targets);
        harmonization.setSourceFileId(upload(dataset.getDataPath(), keycloakId));
        harmonization.setSourceKey(dataset.getPatientIdColumn());
        harmonization.setSourceMergeConfig(dataset.getMerge());
        HarmonizationDTO created = transaction(() -> harmonizationBO.create(harmonization, keycloakId));

        Map<String, ColumnProfile> source = transaction(() -> runBO.fileProfiles(created, keycloakId));
        TransformerWriterSuggestion suggestion = assistant.suggest(created, source, keycloakId);
        created.setTransformer(suggestion.getConnector().getTransformer());
        created.setMapping(suggestion.getConnector().getSchemaMapping());
        int suggested = created.getTransformer().size();
        HarmonizationDTO saved = transaction(() -> harmonizationBO.update(created.getId(), created, keycloakId));
        HarmonizationResultDTO result = transaction(() -> runBO.run(saved, keycloakId));
        List<ColumnProfile> output = transaction(() -> fileBO.profiles(result.getOutputFileId(), keycloakId));

        Set<String> mapped = saved.getMapping().stream().map(ConnectorMappingDTO::getMapping).collect(Collectors.toSet());
        List<String> unmapped = targets.stream().map(HarmonizationTargetDTO::getName).filter(name -> !mapped.contains(name)).toList();
        // Every harmonized cell is compared with the one of the original file the dataset was generated from
        Map<String, Map<String, String>> original = TransformerWriterTruth.originalRows(dataset);
        Map<String, Map<String, String>> harmonized = original.isEmpty() ? Map.of() : TransformerWriterTruth.byKey(
                TransformerWriterTruth.readCsv(transaction(() -> fileBO.loadFile(result.getOutputFileId(), keycloakId)).file()));
        Map<String, Double> wrongRows = new HashMap<>();
        targets.forEach(target -> Optional.ofNullable(TransformerWriterTruth.wrongRows(original, harmonized, target.getName()))
                .ifPresent(wrong -> wrongRows.put(target.getName(), wrong)));
        List<HarmonizationFileColumnResult> columns = reviews.reviewColumns(saved, suggestion.getNodes(), source, reference, output, result,
                        wrongRows).stream()
                .map(column -> column.judged(judge(column.getReview())))
                .toList();
        HarmonizationFileResult evaluated = new HarmonizationFileResult(dataset.getName(), targets.size(), unmapped, suggested,
                saved.getTransformer().size(), result.getRows(), result.getUnmatched().size(), columns);
        Log.infof("[Harmonization-Eval:%s] Mapped %d, judged %d and %d of %d compared with the truth correct of %d columns, "
                        + "%d -> %d steps, mean shift %.2f, %d rejected values, %d keys not in the reference", dataset.getName(),
                evaluated.mapped(), evaluated.correct(), evaluated.truthCorrect(), evaluated.truthCompared(), targets.size(),
                suggested, evaluated.getTransformers(), evaluated.shift(),
                evaluated.rejected(), evaluated.getUnmatched());
        return evaluated;
    }

    private TransformerWriterVerdict judge(TransformerWriterReview review) {
        try {
            return judge.judge(TransformerWriterPrompt.render(review));
        } catch (RuntimeException e) {
            Log.warnf(e, "[Harmonization-Eval:judge] Failed on %s", review.getSchemaNode());
            return null;
        }
    }

    private Long upload(String path, String keycloakId) {
        return transaction(() -> {
            try {
                return fileBO.upload(ResourceLoader.loadAsFile(path), Path.of(path).getFileName().toString(), keycloakId).getId();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static <T> T transaction(Callable<T> work) {
        return QuarkusTransaction.requiringNew().timeout(3600).call(work);
    }
}
