package bio.cosy.feddb.local.agent.etl.transform.organizer;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.analytics.RowProfiler;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.local.agent.etl.transform.agents.TransformerWorkflowAgent;
import bio.cosy.feddb.local.agent.etl.transform.bot.ColumnReview;
import bio.cosy.feddb.local.agent.etl.transform.bot.ColumnReviewBot;
import bio.cosy.feddb.local.agent.etl.transform.bot.ColumnSelectBot;
import bio.cosy.feddb.local.agent.etl.transform.bot.VisitColumnBot;
import bio.cosy.feddb.local.agent.etl.transform.guardrails.AnswerHints;
import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerCombiner;
import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerHelper;
import bio.cosy.feddb.local.agent.etl.transform.model.ColumnCandidate;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.TransformerRunContext;
import bio.cosy.feddb.local.agent.etl.transform.model.VisitMapping;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerColumnTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerUserTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerValidateTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerWriterTool;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeEntityMapper;
import bio.cosy.feddb.local.agent.text.SapbertSimilartity;
import bio.cosy.feddb.local.helper.text.TextSimilarity;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@ApplicationScoped
public class TransformerOrganizer {

    public static final String SELECTION_FAILED = "Transformer selection failed: ";
    private static final int COLUMN_SELECT_ATTEMPTS = 2;
    private static final int MAX_LISTED_ALLOWED_VALUES = 100;
    private static final int CLOSEST_ALLOWED_VALUES = 5;

    @Inject
    ColumnSelectBot columnSelectBot;

    @Inject
    ColumnReviewBot columnReviewBot;

    @Inject
    SapbertSimilartity sapbert;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TransformerWorkflowAgent transformerWorkflow;

    @Inject
    VisitColumnBot visitColumnBot;

    @Inject
    TransformerValidateTool validateTool;

    @Inject
    TransformerUserTool userTool;

    @Inject
    TransformerColumnTool columnTool;

    @Inject
    TransformerWriterTool writerTool;

    @Inject
    DataTypeEntityMapper dataTypeMapper;

    @Inject
    TransformerCombiner combiner;

    @Inject
    AnswerHints hints;

    @ActivateRequestContext
    public ConnectorSelectedTransformer selectTransformer(TransformerRunContext context,
                                                          TableSample previewData,
                                                          LocalSchemaNodeDTO schemaNode,
                                                          String instructions,
                                                          List<ColumnCandidate> candidates,
                                                          DataTypeDTO dataType) {
        DataTypeNodeDTO nodeDTO = dataTypeMapper.dtoToNodeDto(dataType);
        userTool.init(context.user(), previewData.getColumns());
        columnTool.init(context, previewData.getColumns());

        boolean clear = !candidates.isEmpty() && candidates.getFirst().isClear();
        List<String> columns;
        if (clear) {
            columns = List.of(candidates.getFirst().getColumn());
            context.step("matcher: '%s' fits by name", columns.getFirst());
        } else {
            try {
                columns = reviewColumns(context, previewData, schemaNode, instructions, candidates,
                        selectColumns(context, previewData, schemaNode, instructions, candidates));
            } catch (RuntimeException e) {
                return ConnectorSelectedTransformer.notFound(SELECTION_FAILED + "the column selection failed: " + e.getMessage());
            }
        }
        if (columns.isEmpty()) {
            return ConnectorSelectedTransformer.notFound("No source column holds the information of this schema node");
        }
        ConnectorSelectedTransformer selected = selectForColumns(context, previewData, schemaNode, instructions, columns, nodeDTO);
        if (!selected.isFoundTransformer() && clear) {
            List<String> asked;
            try {
                asked = selectColumns(context, previewData, schemaNode, instructions, candidates);
            } catch (RuntimeException e) {
                asked = List.of();
            }
            if (!asked.isEmpty() && !Set.copyOf(asked).equals(Set.copyOf(columns))) {
                selected = selectForColumns(context, previewData, schemaNode, instructions, asked, nodeDTO);
            }
        }
        return selected;
    }

    @ActivateRequestContext
    public VisitMapping selectVisit(TransformerRunContext context,
                                    TableSample previewData,
                                    LocalSchemaNodeDTO schemaNode,
                                    String instructions,
                                    ConnectorSelectedTransformer selected) {
        String patientIdColumn = context.patientIdColumn();
        if (!TransformerHelper.hasSeveralRowsPerPatient(previewData, patientIdColumn, context.fileProfiles())) {
            return null;
        }
        try {
            VisitMapping visit = visitColumnBot.select(schemaNode, selected.getMappedColumn(), patientIdColumn,
                    TransformerHelper.columnExamples(previewData), instructions.strip());
            return TransformerHelper.checkVisitMapping(visit, previewData, patientIdColumn);
        } catch (RuntimeException e) {
            Log.warnf(e, "Visit selection for schema node %s failed", schemaNode.getName());
            return null;
        }
    }

    private ConnectorSelectedTransformer selectForColumns(TransformerRunContext context,
                                                          TableSample previewData,
                                                          LocalSchemaNodeDTO schemaNode,
                                                          String instructions,
                                                          List<String> columns,
                                                          DataTypeNodeDTO nodeDTO) {
        String patientIdColumn = context.patientIdColumn();
        String hints = instructions;
        validateTool.init(context, schemaNode, nodeDTO, columns,
                TransformerHelper.valueRows(previewData, columns, context.fileProfiles(), patientIdColumn));
        writerTool.init(context, schemaNode, nodeDTO, previewData);
        this.hints.reset();

        if (columns.size() == 1) {
            ConnectorSelectedTransformer direct = ConnectorSelectedTransformer.mapping(columns.getFirst(), List.of());
            ConnectorValidationResultDTO validation = validateTool.validate(direct);
            if (validation.isValid() && checksValues(nodeDTO, columns.getFirst(), schemaNode)) {
                return direct;
            }
            hints += validation.isValid()
                    ? "\nMapping column '" + columns.getFirst() + "' directly passes the validation, but it only checks "
                    + "the type, so check yourself that the values mean the same, e.g. in unit, scale and missing-value marker."
                    : "\nMapping column '" + columns.getFirst() + "' directly is rejected: " + validation.getMessage();
            String related = columnTool.relatedColumns(columns.getFirst());
            if (!related.isEmpty()) {
                hints += "\nOther columns with mostly the same values, to compare with; whether their values mean the same "
                        + "here and whether their mappings are right, you decide:\n" + related;
            }
        }

        // A long list of allowed values hides the fitting ones, the agent sees those closest to the values of the file
        LocalSchemaNodeDTO shown = schemaNode;
        Map<String, List<String>> closest = closestAllowedValues(schemaNode, columns, context.fileProfiles());
        if (!closest.isEmpty()) {
            shown = objectMapper.convertValue(schemaNode, LocalSchemaNodeDTO.class);
            shown.getDataType().setAllowedValues(closest.values().stream().flatMap(List::stream)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            hints += "\nThe data type has " + schemaNode.getDataType().getAllowedValues().size() + " allowed values, "
                    + "only the ones closest in meaning to the values of the file are listed; every allowed value is "
                    + "accepted. Closest per value: " + closest.entrySet().stream()
                    .map(entry -> "'" + entry.getKey() + "': " + String.join(" | ", entry.getValue()))
                    .collect(Collectors.joining("; "));
        }

        List<String> keep = new ArrayList<>(columns);
        if (patientIdColumn != null && !keep.contains(patientIdColumn)) {
            keep.add(patientIdColumn);
        }
        ConnectorSelectedTransformer selected = runWorkflow(previewData.filterColumns(keep), shown, columns,
                hints.strip(), TransformerHelper.describeFileValues(columns, context.fileProfiles()));
        if (!selected.isFoundTransformer()) {
            // A custom transformer written for this node but not taken is not worth keeping
            writerTool.discard();
        }
        return selected;
    }

    /**
     * Whether a valid direct mapping needs no agent: categories pin the values, a number or date only when the column
     * has the name of the node, else it can have another unit; free text accepts any missing-value marker.
     */
    private static boolean checksValues(DataTypeNodeDTO dataType, String column, LocalSchemaNodeDTO schemaNode) {
        return dataType == null || dataType.getOptions() != null && !dataType.getOptions().isEmpty()
                || dataType.getType() != DataTypes.STRING && TextSimilarity.nameSimilarity(column, schemaNode.getName()) == 1;
    }

    private ConnectorSelectedTransformer runWorkflow(TableSample selectedData, LocalSchemaNodeDTO schemaNode,
                                                     List<String> columns, String hints, String fileValues) {
        ConnectorSelectedTransformer selected;
        try {
            selected = transformerWorkflow.select(selectedData, schemaNode, columns, hints, fileValues);
        } catch (RuntimeException e) {
            Log.warnf(e, "Transformer selection for schema node %s failed", schemaNode.getName());
            return ConnectorSelectedTransformer.notFound(SELECTION_FAILED + e.getMessage());
        }
        if (selected == null) {
            return ConnectorSelectedTransformer.notFound("No transformer found");
        }
        if (!selected.isFoundTransformer()) {
            return selected;
        }
        selected.getSelectedTransformers().forEach(combiner::normalize);
        ConnectorValidationResultDTO validation = validateTool.validate(selected);
        if (!validation.isValid()) {
            selected.setFoundTransformer(false);
            selected.setNeededTransformerFunction(validation.getMessage());
        }
        return selected;
    }

    // A second opinion, as the selection tends to fill a node with a related fact; the selection decides again
    private List<String> reviewColumns(TransformerRunContext context, TableSample previewData, LocalSchemaNodeDTO schemaNode,
                                       String instructions, List<ColumnCandidate> candidates, List<String> columns) {
        if (columns.isEmpty()) {
            return columns;
        }
        ColumnReview review;
        try {
            review = columnReviewBot.review(schemaNode, TransformerHelper.describeFileValues(columns, context.fileProfiles()),
                    TransformerHelper.columnExamples(previewData));
        } catch (RuntimeException e) {
            Log.warnf(e, "Reviewing the columns %s of schema node %s failed, keeping them", columns, schemaNode.getName());
            return columns;
        }
        if (review == null || review.isHeld() || review.getReason() == null || review.getReason().isBlank()) {
            return columns;
        }
        context.step("reviewColumns: %s not held, %s", columns, review.getReason());
        return selectColumns(context, previewData, schemaNode, instructions + "\nA review of your selection " + columns
                + " disagrees: " + review.getReason() + " Select again, or return an empty list if no column records it.",
                candidates);
    }

    // Every value of the file with the allowed values closest in meaning; empty for a short list or without embeddings
    private Map<String, List<String>> closestAllowedValues(LocalSchemaNodeDTO schemaNode, List<String> columns,
                                                           Map<String, ColumnProfile> profiles) {
        Set<Object> allowed = schemaNode.getDataType() == null ? null : schemaNode.getDataType().getAllowedValues();
        if (allowed == null || allowed.size() <= MAX_LISTED_ALLOWED_VALUES) {
            return Map.of();
        }
        List<String> values = columns.stream()
                .flatMap(column -> TransformerHelper.fileValues(profiles.get(column)).stream())
                .filter(value -> !RowProfiler.isMissing(value))
                .distinct()
                .toList();
        List<List<String>> closest = sapbert.closest(values, allowed.stream().map(String::valueOf).toList(), CLOSEST_ALLOWED_VALUES);
        Map<String, List<String>> byValue = new LinkedHashMap<>();
        if (closest != null) {
            IntStream.range(0, values.size()).forEach(index -> byValue.put(values.get(index), closest.get(index)));
        }
        return byValue;
    }

    private List<String> selectColumns(TransformerRunContext context, TableSample previewData, LocalSchemaNodeDTO schemaNode,
                                       String hints, List<ColumnCandidate> candidates) {
        // A column can hold several nodes, e.g. an age the age at diagnosis and at index
        List<ColumnCandidate> annotated = candidates.stream()
                .map(candidate -> context.mappedColumns().containsKey(candidate.getColumn())
                        ? candidate.withNote("already holds " + context.mappedColumns().get(candidate.getColumn())
                        + ", it can hold this node as well if it records it")
                        : candidate)
                .toList();
        RuntimeException failure = null;
        for (int attempt = 0; attempt < COLUMN_SELECT_ATTEMPTS; attempt++) {
            try {
                userTool.askedBy(ColumnSelectBot.NAME);
                List<String> selected = columnSelectBot.select(previewData, schemaNode, hints.strip(), annotated,
                        TransformerHelper.columnExamples(previewData));
                List<String> columns = selected == null ? List.of() : selected.stream()
                        .flatMap(answer -> columnNames(answer, previewData.getColumns()))
                        .distinct()
                        .toList();
                context.step("%s: %s of %s", ColumnSelectBot.NAME, columns, candidates.stream().map(ColumnCandidate::getColumn).toList());
                return columns;
            } catch (RuntimeException e) {
                Log.warnf(e, "Column selection for schema node %s failed", schemaNode.getName());
                failure = e;
            }
        }
        context.step("%s failed: %s", ColumnSelectBot.NAME, failure.getMessage());
        throw failure;
    }

    private Stream<String> columnNames(String answer, List<String> columns) {
        if (columns.contains(answer)) {
            return Stream.of(answer);
        }
        return Arrays.stream(answer.split(","))
                .map(name -> name.replaceAll("^[\\s\\[\"'`]+|[\\s\\]\"'`]+$", ""))
                .filter(columns::contains);
    }
}
