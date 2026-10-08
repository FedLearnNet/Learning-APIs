package eval.agent.etl.transform.judge;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerListTool;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorHelper;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.statistics.TransformedStatisticsBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerBO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeEntityMapper;
import bio.cosy.feddb.local.api.schema.schemanode.LocalSchemaNodeWithPathDTO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeBO;
import eval.agent.etl.transform.pipeline.TransformerWriterAssistant;
import eval.agent.etl.transform.pipeline.TransformerWriterImporter;
import eval.agent.etl.transform.pojo.TransformerWriterReview;
import eval.agent.etl.transform.pojo.TransformerWriterTransformation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class TransformerWriterReviews {

    private static final int MAPPED_COLUMN_VALUES = 25;
    private static final int OTHER_COLUMN_VALUES = 5;
    private static final int IMPORTED_VALUES = 25;

    @Inject
    SchemaNodeBO schemaNodeBO;

    @Inject
    DataTypeEntityMapper dataTypeMapper;

    @Inject
    TransformedStatisticsBO statisticsBO;

    @Inject
    CodeTransformerBO codeTransformerBO;

    @Inject
    TransformerWriterImporter importer;

    public List<TransformerWriterReview> of(ConnectorDTO connector, List<TransformerAssistantMessageDTO> nodes,
                                            ConnectorRunDTO run) {
        Map<Long, LocalSchemaNodeWithPathDTO> schemaNodes = loadSchemaNodes(connector.getCohortId());
        Map<String, CodeTransformerDTO> code = loadCode();
        Map<String, ColumnProfile> sourceColumns = loadSourceColumns(connector);
        String importRun = importer.describe(run, connector.getCohortId());

        List<TransformerWriterReview> reviews = new ArrayList<>();
        for (TransformerAssistantMessageDTO node : nodes) {
            LocalSchemaNodeWithPathDTO schemaNode = schemaNodes.get(node.getSchemaId());
            TransformerWriterReview review = createReview(schemaNode, node.getMappingPath(), importRun);
            if (TransformerWriterAssistant.isFound(node)) {
                addMapping(review, node.getSelectedTransformer(), code, sourceColumns);
                review.setVisitMapping(node.getVisitMapping());
                addImport(review, schemaNode, connector.getCohortId(), run);
            } else {
                addNotFound(review, node.getSelectedTransformer(), sourceColumns);
            }
            reviews.add(review);
        }
        return reviews;
    }

    @Transactional
    Map<Long, LocalSchemaNodeWithPathDTO> loadSchemaNodes(Long cohortId) {
        return schemaNodeBO.getSchemaNodesWithPathForCohort(cohortId).stream()
                .collect(Collectors.toMap(LocalSchemaNodeWithPathDTO::getId, Function.identity()));
    }

    @Transactional
    Map<String, CodeTransformerDTO> loadCode() {
        return codeTransformerBO.list().stream()
                .filter(code -> code.getImageName() != null)
                .collect(Collectors.toMap(CodeTransformerDTO::getImageName, Function.identity(), (first, second) -> first));
    }

    private Map<String, ColumnProfile> loadSourceColumns(ConnectorDTO connector) {
        return statisticsBO.getFileStatistics(connector.getCohortId(), connector.getInputConfig(),
                        connector.getUploadInfo(), connector.getMergeConfig(), connector.getPivotConfig()).stream()
                .collect(Collectors.toMap(ColumnProfile::name, Function.identity(), (first, second) -> first));
    }

    private TransformerWriterReview createReview(LocalSchemaNodeWithPathDTO node, String mappingPath, String importRun) {
        TransformerWriterReview review = new TransformerWriterReview();
        review.setSchemaNode(mappingPath);
        review.setDescription(node.getDescription());
        review.setDataType(describe(dataTypeMapper.dtoToNodeDto(node.getDataType())));
        review.setImportRun(importRun);
        return review;
    }

    private static void addMapping(TransformerWriterReview review, ConnectorSelectedTransformer selected,
                                   Map<String, CodeTransformerDTO> code, Map<String, ColumnProfile> sourceColumns) {
        review.setFound(true);
        review.setMappedColumn(selected.getMappedColumn());
        selected.getSelectedTransformers().forEach(transformer ->
                review.getTransformations().add(createTransformation(transformer, code.get(transformer.getAppImage()))));
        readColumns(selected).stream()
                .map(sourceColumns::get)
                .filter(Objects::nonNull)
                .forEach(column -> review.getSourceColumns().add(profile(column, MAPPED_COLUMN_VALUES)));
    }

    private void addImport(TransformerWriterReview review, LocalSchemaNodeWithPathDTO node, Long cohortId,
                           ConnectorRunDTO run) {
        Map<String, Long> importedValues = importer.storedValues(cohortId, node.getId());
        Map<String, Long> rejections = importer.rejectedValues(run, node, review.getMappedColumn());
        review.setImportedEntries(sum(importedValues));
        review.setImportedValues(mostFrequent(importedValues));
        review.setRejectedValues(sum(rejections));
        review.setRejections(mostFrequent(rejections));
    }

    private static void addNotFound(TransformerWriterReview review, ConnectorSelectedTransformer selected,
                                    Map<String, ColumnProfile> sourceColumns) {
        review.setNotFoundReason(selected == null ? null : selected.getNeededTransformerFunction());
        sourceColumns.values().forEach(column -> review.getSourceColumns().add(profile(column, OTHER_COLUMN_VALUES)));
    }

    private static String describe(DataTypeNodeDTO dataType) {
        StringBuilder text = new StringBuilder(String.valueOf(dataType.getType()));
        if (dataType.getOptions() != null && !dataType.getOptions().isEmpty()) {
            text.append(", allowed values ").append(dataType.getOptions());
        }
        if (dataType.getValidations() != null) {
            dataType.getValidations().forEach(validation ->
                    text.append(", ").append(validation.getName()).append(' ').append(validation.getValidator()));
        }
        return text.append(dataType.isRequired() ? ", a value is required" : "")
                .append(dataType.allowNullValues() ? ", empty values allowed" : ", empty values not allowed")
                .toString();
    }

    private static Set<String> readColumns(ConnectorSelectedTransformer selected) {
        Set<String> columns = new LinkedHashSet<>();
        columns.add(selected.getMappedColumn());
        for (ConnectorTransformerDTO transformer : selected.getSelectedTransformers()) {
            columns.addAll(ConnectorHelper.commaSeparated(transformer.getColumn()));
            if (transformer.getInputMapping() != null) {
                transformer.getInputMapping().values().forEach(column -> columns.add(String.valueOf(column)));
            }
        }
        return columns;
    }

    private static TransformerWriterTransformation createTransformation(ConnectorTransformerDTO transformer,
                                                                        CodeTransformerDTO code) {
        StringBuilder configuration = new StringBuilder(TransformerListTool.isCodeTransformer(transformer) && code != null
                ? "Custom code transformer '" + code.getName() + "' (" + code.getMode() + "): " + code.getDescription()
                : transformer.getModuleName() + " / " + transformer.getMethodName());
        appendIfPresent(configuration, "column", transformer.getColumn());
        appendIfPresent(configuration, "input", transformer.getInputMapping());
        appendIfPresent(configuration, "output", transformer.getReturnMapping());
        appendIfPresent(configuration, "parameters", transformer.getHyperparams());
        return new TransformerWriterTransformation(configuration.toString(), code == null ? null : code.getBody());
    }

    private static String profile(ColumnProfile column, int maxValues) {
        StringBuilder text = new StringBuilder(column.name()).append(" (").append(column.type()).append(", ")
                .append(column.missing()).append(" of ").append(column.count()).append(" empty");
        if (column.min() != null) {
            text.append(", ").append(column.min()).append(" to ").append(column.max());
        }
        text.append(')');
        List<Entry<String, Integer>> values = column.valueCounts() != null ? column.valueCounts() : column.topCategories();
        if (values == null || values.isEmpty()) {
            return text.toString();
        }
        text.append(": ").append(values.stream()
                .sorted(Entry.<String, Integer>comparingByValue().reversed())
                .limit(maxValues)
                .map(entry -> "'" + entry.getKey() + "' x" + entry.getValue())
                .collect(Collectors.joining(", ")));
        if (values.size() > maxValues) {
            text.append(", ... ").append(column.uniqueValues()).append(" distinct values");
        }
        return text.toString();
    }

    private static long sum(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    private static Map<String, Long> mostFrequent(Map<String, Long> counts) {
        Map<String, Long> top = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Entry.<String, Long>comparingByValue().reversed())
                .limit(IMPORTED_VALUES)
                .forEach(entry -> top.put(entry.getKey(), entry.getValue()));
        return top;
    }

    private static void appendIfPresent(StringBuilder text, String label, Object value) {
        boolean empty = value == null || value instanceof String string && string.isBlank()
                || value instanceof Map<?, ?> map && map.isEmpty();
        if (!empty) {
            text.append("; ").append(label).append(' ').append(value);
        }
    }
}
