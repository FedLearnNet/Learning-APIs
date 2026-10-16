package eval.agent.etl.gdc.score;

import bio.cosy.feddb.core.helper.ResourceLoader;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.connector.ConnectorHelper;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import eval.agent.etl.gdc.pojo.GdcFieldResult;
import eval.agent.etl.gdc.pojo.GdcVerdict;
import eval.agent.etl.gdc.setting.GdcStudy;
import eval.agent.etl.transform.setting.TransformerWriterTruth;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compares the mapping of every GDC field with the ground truth of the benchmark. The ground truth names, per study,
 * the columns a field is taken from; one column can hold several fields (e.g. an age the age at diagnosis and the age
 * at index) and a field can come from several columns. A field counts as correct when the agent reads one of them.
 */
public final class GdcScoring {

    private GdcScoring() {
    }

    public static Map<String, Set<String>> groundTruth(GdcStudy study) {
        Map<String, Set<String>> columns = new LinkedHashMap<>();
        TransformerWriterTruth.readCsv(resource(study.groundTruthPath())).forEach(row -> columns
                .computeIfAbsent(row.get("GDC_format_variable_names"), field -> new LinkedHashSet<>())
                .add(row.get("original_paper_variable_names")));
        return columns;
    }

    public static Set<String> studyColumns(GdcStudy study) {
        List<Map<String, String>> rows = TransformerWriterTruth.readCsv(resource(study.dataPath()));
        return rows.isEmpty() ? Set.of() : rows.getFirst().keySet();
    }

    /** The result of every schema node the agent answered for, plus a missed one for every unanswered field. */
    public static List<GdcFieldResult> score(GdcStudy study, List<TransformerAssistantMessageDTO> nodes) {
        Map<String, Set<String>> truth = groundTruth(study);
        Set<String> columns = studyColumns(study);
        List<GdcFieldResult> results = new ArrayList<>();
        Set<String> answered = new HashSet<>();
        for (TransformerAssistantMessageDTO node : nodes) {
            String field = field(node.getMappingPath());
            answered.add(field);
            ConnectorSelectedTransformer selected = node.getSelectedTransformer();
            List<String> mapped = sourceColumns(selected, columns);
            List<String> expected = List.copyOf(truth.getOrDefault(field, Set.of()));
            results.add(new GdcFieldResult(study.getName(), field, expected, mapped, configuration(selected),
                    verdict(expected, mapped, selected != null && selected.isFoundTransformer())));
        }
        truth.forEach((field, expected) -> {
            if (!answered.contains(field)) {
                results.add(new GdcFieldResult(study.getName(), field, List.copyOf(expected), List.of(), "not answered",
                        GdcVerdict.MISSED));
            }
        });
        return results;
    }

    private static GdcVerdict verdict(List<String> expected, List<String> mapped, boolean found) {
        if (!found) {
            return expected.isEmpty() ? GdcVerdict.EMPTY : GdcVerdict.MISSED;
        }
        if (expected.isEmpty()) {
            return GdcVerdict.EXTRA;
        }
        return mapped.stream().anyMatch(expected::contains) ? GdcVerdict.CORRECT : GdcVerdict.WRONG_COLUMN;
    }

    // The columns of the study a mapping reads: the mapped column itself and the inputs of its transformers
    private static List<String> sourceColumns(ConnectorSelectedTransformer selected, Set<String> columns) {
        if (selected == null || !selected.isFoundTransformer()) {
            return List.of();
        }
        return Stream.concat(Stream.of(selected.getMappedColumn()), selected.getSelectedTransformers().stream()
                        .flatMap(transformer -> ConnectorHelper.transformerInputs(transformer).stream()))
                .filter(columns::contains)
                .distinct()
                .toList();
    }

    private static String configuration(ConnectorSelectedTransformer selected) {
        if (selected == null || !selected.isFoundTransformer()) {
            return selected == null ? "nothing" : "not found: " + selected.getNeededTransformerFunction();
        }
        return "'" + selected.getMappedColumn() + "'" + (selected.getSelectedTransformers().isEmpty() ? " directly"
                : " with " + selected.getSelectedTransformers().stream()
                .map(ConnectorTransformerDTO::getMethodName)
                .collect(Collectors.joining(", ")));
    }

    // The schema node of a mapping path, e.g. 'age_at_diagnosis' of 'GDC.age_at_diagnosis'
    private static String field(String mappingPath) {
        return mappingPath == null ? "" : mappingPath.substring(mappingPath.lastIndexOf('.') + 1);
    }

    private static File resource(String path) {
        try {
            return ResourceLoader.loadAsFile(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
