package eval.agent.etl.harmonization.judge;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.api.harmonization.HarmonizationConnectorMapper;
import bio.cosy.feddb.local.api.harmonization.HarmonizationDTO;
import bio.cosy.feddb.local.api.harmonization.HarmonizationResultDTO;
import bio.cosy.feddb.local.api.harmonization.HarmonizationTargetDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerDTO;
import eval.agent.etl.harmonization.pojo.HarmonizationFileColumnResult;
import eval.agent.etl.transform.judge.TransformerWriterReviews;
import eval.agent.etl.transform.pipeline.TransformerWriterAssistant;
import eval.agent.etl.transform.pojo.TransformerWriterReview;
import eval.agent.etl.transform.setting.TransformerWriterTruth;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

//Scores how well a dataset was harmonized onto the reference file, column by column of the reference
@ApplicationScoped
public class HarmonizationFileReviews {

    private static final int REFERENCE_VALUES = 25;
    private static final String REJECTED = "not a valid value of the reference column, left empty";

    @Inject
    TransformerWriterReviews reviews;

    @Inject
    HarmonizationConnectorMapper connectorMapper;

    public List<HarmonizationFileColumnResult> reviewColumns(HarmonizationDTO harmonization,
                                                             List<TransformerAssistantMessageDTO> suggestions,
                                                             Map<String, ColumnProfile> source, List<ColumnProfile> reference,
                                                             List<ColumnProfile> output, HarmonizationResultDTO result,
                                                             Map<String, Double> wrongRows) {
        Map<String, CodeTransformerDTO> code = reviews.loadCode();
        Map<String, ColumnProfile> references = byName(reference);
        Map<String, ColumnProfile> outputs = byName(output);
        Map<Long, TransformerAssistantMessageDTO> suggestionByTarget = suggestions.stream()
                .collect(Collectors.toMap(TransformerAssistantMessageDTO::getSchemaId, Function.identity(), (a, b) -> b));
        List<HarmonizationTargetDTO> targets = harmonization.getTargets();
        List<HarmonizationFileColumnResult> columns = new ArrayList<>();

        for (int index = 0; index < targets.size(); index++) {
            HarmonizationTargetDTO target = targets.get(index);
            ColumnProfile referenceColumn = references.get(target.getName());
            ColumnProfile outputColumn = outputs.get(target.getName());
            Map<String, Long> harmonizedValues = harmonizedValues(referenceColumn, outputColumn);
            int rejected = result.getRejected().getOrDefault(target.getName(), 0);

            TransformerWriterReview review = reviewForJudge(index, target, referenceColumn, result.getUnmatched().size());
            addSuggestion(review, suggestionByTarget.get(HarmonizationConnectorMapper.targetId(index)), code, source);
            TransformerWriterReviews.addValues(review, harmonizedValues, rejected > 0 ? Map.of(REJECTED, (long) rejected) : Map.of());
            columns.add(new HarmonizationFileColumnResult(target.getName(), review, null,
                    shiftFromReference(referenceColumn, outputColumn, harmonizedValues),
                    emptyShareOfHarmonized(referenceColumn, outputColumn), emptyShare(referenceColumn), rejected,
                    wrongRows.get(target.getName())));
        }
        return columns;
    }

    private TransformerWriterReview reviewForJudge(int index, HarmonizationTargetDTO target, ColumnProfile referenceColumn,
                                                   int unmatchedKeys) {
        return reviews.createReview(connectorMapper.toSchemaNode(target, index), target.getName(), String.format(
                "The source is harmonized onto the reference file, %d of its keys are not in it. The column in the "
                        + "reference file: %s", unmatchedKeys,
                referenceColumn == null ? "-" : TransformerWriterReviews.profile(referenceColumn, REFERENCE_VALUES)));
    }

    private void addSuggestion(TransformerWriterReview review, TransformerAssistantMessageDTO suggestion,
                               Map<String, CodeTransformerDTO> code, Map<String, ColumnProfile> source) {
        if (suggestion != null && TransformerWriterAssistant.isFound(suggestion)) {
            TransformerWriterReviews.addMapping(review, suggestion.getSelectedTransformer(), code, source);
        } else {
            TransformerWriterReviews.addNotFound(review, suggestion == null ? null : suggestion.getSelectedTransformer(), source);
        }
    }

    private Map<String, Long> harmonizedValues(ColumnProfile reference, ColumnProfile output) {
        Map<String, Long> counts = new LinkedHashMap<>(valueCounts(output));
        valueCounts(reference).forEach((value, count) -> counts.merge(value, -count, Long::sum));
        counts.values().removeIf(count -> count <= 0);
        return counts;
    }

    private Double shiftFromReference(ColumnProfile reference, ColumnProfile output, Map<String, Long> harmonizedValues) {
        if (reference == null || output == null || isIdentifier(reference)) {
            return null;
        }
        if (reference.valueCounts() != null && output.valueCounts() != null) {
            return TransformerWriterTruth.distance(harmonizedValues, valueCounts(reference));
        }
        return meanShiftInStandardDeviations(reference, output);
    }

    private boolean isIdentifier(ColumnProfile column) {
        return column.uniqueValues() > (column.count() - column.missing()) / 2;
    }

    private Double meanShiftInStandardDeviations(ColumnProfile reference, ColumnProfile output) {
        long referenceValues = reference.count() - reference.missing();
        long outputValues = output.count() - output.missing();
        if (reference.mean() == null || output.mean() == null || reference.std() == null || reference.std() == 0
                || outputValues <= referenceValues) {
            return null;
        }
        double harmonizedMean = (output.mean() * outputValues - reference.mean() * referenceValues) / (outputValues - referenceValues);
        return Math.abs(harmonizedMean - reference.mean()) / reference.std();
    }

    private double emptyShareOfHarmonized(ColumnProfile reference, ColumnProfile output) {
        return reference == null || output == null ? 1
                : share(output.missing() - reference.missing(), output.count() - reference.count());
    }

    private double emptyShare(ColumnProfile column) {
        return column == null ? 0 : share(column.missing(), column.count());
    }

    private Map<String, Long> valueCounts(ColumnProfile column) {
        return column == null || column.valueCounts() == null ? Map.of() : column.valueCounts().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> (long) entry.getValue(), Long::sum, LinkedHashMap::new));
    }

    private double share(long part, long total) {
        return total <= 0 ? 0 : (double) part / total;
    }

    private Map<String, ColumnProfile> byName(List<ColumnProfile> profiles) {
        return profiles.stream().collect(Collectors.toMap(ColumnProfile::name, Function.identity(), (a, b) -> a));
    }
}
