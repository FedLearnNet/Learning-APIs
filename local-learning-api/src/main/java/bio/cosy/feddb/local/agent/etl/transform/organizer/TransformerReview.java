package bio.cosy.feddb.local.agent.etl.transform.organizer;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.analytics.RowProfiler;
import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerHelper;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.Recheck;
import bio.cosy.feddb.local.agent.etl.transform.model.TransformerRunContext;
import bio.cosy.feddb.local.api.importer.connector.ConnectorHelper;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformationBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.schema.schemanode.LocalSchemaNodeWithPathDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.stream.Stream;


@ApplicationScoped
public class TransformerReview {

    private static final int MIN_PARTLY_USED_VALUES = 4;

    @Inject
    ConnectorTransformationBO transformationBO;

    public Optional<Recheck> leftover(List<LocalSchemaNodeWithPathDTO> nodes,
                                      Map<Long, ConnectorSelectedTransformer> selected,
                                      TableSample original,
                                      TransformerRunContext context) {
        Set<String> used = new HashSet<>(context.mappedColumns().keySet());
        Optional.ofNullable(context.patientIdColumn()).ifPresent(used::add);
        context.config().getTransformer().forEach(transformer -> used.addAll(ConnectorHelper.transformerInputs(transformer)));
        List<String> unused = original.getColumns().stream()
                .filter(column -> !used.contains(column) && hasValues(column, original, context))
                .toList();
        Map<String, String> partlyUsed = partlyUsed(nodes, selected, original, context);
        List<String> columns = Stream.concat(unused.stream(), partlyUsed.keySet().stream()).distinct().toList();
        if (columns.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Recheck((unused.isEmpty() ? "" : "The columns " + unused + " hold values, but no schema "
                + "node uses them yet. ") + String.join(" ", partlyUsed.values()) + " Check whether one of them holds "
                + "this schema node, also when its name or its values look different.", columns));
    }

    private Map<String, String> partlyUsed(List<LocalSchemaNodeWithPathDTO> nodes,
                                           Map<Long, ConnectorSelectedTransformer> selected,
                                           TableSample original,
                                           TransformerRunContext context) {
        Map<String, String> partlyUsed = new LinkedHashMap<>();
        for (LocalSchemaNodeWithPathDTO node : nodes) {
            ConnectorSelectedTransformer mapping = selected.get(node.getId());
            Optional<String> column = sourceColumn(mapping, context);
            long values = column.map(name -> TransformerHelper.fileValues(context.fileProfiles().get(name)).stream()
                    .filter(value -> !RowProfiler.isMissing(value)).count()).orElse(0L);
            if (values < MIN_PARTLY_USED_VALUES || column.get().equals(context.patientIdColumn())
                    || partlyUsed.containsKey(column.get())) {
                continue;
            }
            results(mapping, column.get(), original, context)
                    .filter(results -> 2 * results < values)
                    .ifPresent(results -> partlyUsed.put(column.get(), "Column '" + column.get() + "' is used by "
                            + node.getPath() + ", which turns its " + values + " different values into " + results
                            + " results, so it may hold more; a column that already holds another schema node is read "
                            + "into a new column."));
        }
        return partlyUsed;
    }

    private static Optional<String> sourceColumn(ConnectorSelectedTransformer selected, TransformerRunContext context) {
        if (selected == null || !selected.isFoundTransformer()
                || selected.getSelectedTransformers().stream().anyMatch(ConnectorTransformerDTO::isAppBased)) {
            return Optional.empty();
        }
        List<ConnectorTransformerDTO> transformers = selected.getSelectedTransformers();
        List<String> inputs = (transformers.isEmpty() ? Stream.of(selected.getMappedColumn()) : transformers.stream()
                .flatMap(transformer -> ConnectorHelper.transformerInputs(transformer).stream()))
                .filter(context.fileProfiles()::containsKey)
                .distinct()
                .toList();
        return inputs.size() == 1 ? Optional.of(inputs.getFirst()) : Optional.empty();
    }

    private Optional<Long> results(ConnectorSelectedTransformer selected, String column, TableSample original,
                                   TransformerRunContext context) {
        List<Map<String, Object>> rows = TransformerHelper.valueRows(original, List.of(column), context.fileProfiles(),
                context.patientIdColumn());
        try {
            List<Map<String, Object>> converted = transformationBO.applyBuiltInTransformations(
                    selected.getSelectedTransformers(), rows, context.patientIdColumn());
            return converted.size() != rows.size() ? Optional.empty() : Optional.of(converted.stream()
                    .map(row -> row.get(selected.getMappedColumn()))
                    .filter(value -> value != null && !String.valueOf(value).isBlank())
                    .map(String::valueOf)
                    .distinct()
                    .count());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean hasValues(String column, TableSample original, TransformerRunContext context) {
        ColumnProfile profile = context.fileProfiles().get(column);
        return profile != null ? TransformerHelper.fileValues(profile).stream().anyMatch(value -> !RowProfiler.isMissing(value))
                : original.getValues().getOrDefault(column, List.of()).stream().anyMatch(value -> !String.valueOf(value).isBlank());
    }
}
