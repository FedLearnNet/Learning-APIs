package bio.cosy.feddb.local.agent.etl.transform.organizer;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.local.agent.etl.transform.agents.TransformerWorkflowAgent;
import bio.cosy.feddb.local.agent.etl.transform.bot.ColumnSelectBot;
import bio.cosy.feddb.local.agent.etl.transform.bot.VisitColumnBot;
import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerCombiner;
import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerHelper;
import bio.cosy.feddb.local.agent.etl.transform.model.ColumnCandidate;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.TransformerRunContext;
import bio.cosy.feddb.local.agent.etl.transform.model.VisitMapping;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerUserTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerValidateTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerWriterTool;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeEntityMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@ApplicationScoped
public class TransformerOrganizer {

    @Inject
    ColumnSelectBot columnSelectBot;

    @Inject
    TransformerWorkflowAgent transformerWorkflow;

    @Inject
    VisitColumnBot visitColumnBot;

    @Inject
    TransformerValidateTool validateTool;

    @Inject
    TransformerUserTool userTool;

    @Inject
    TransformerWriterTool writerTool;

    @Inject
    DataTypeEntityMapper dataTypeMapper;

    @Inject
    TransformerCombiner combiner;

    @ActivateRequestContext
    public ConnectorSelectedTransformer selectTransformer(TransformerRunContext context,
                                                          TableSample previewData,
                                                          LocalSchemaNodeDTO schemaNode,
                                                          String instructions,
                                                          List<ColumnCandidate> candidates,
                                                          DataTypeDTO dataType) {
        DataTypeNodeDTO nodeDTO = dataTypeMapper.dtoToNodeDto(dataType);
        userTool.init(context.user(), previewData.getColumns());

        boolean clear = !candidates.isEmpty() && candidates.getFirst().isClear();
        List<String> columns;
        if (clear) {
            columns = List.of(candidates.getFirst().getColumn());
            context.step("matcher: '%s' fits by name", columns.getFirst());
        } else {
            columns = selectColumns(context, previewData, schemaNode, instructions, candidates);
        }
        if (columns.isEmpty()) {
            return ConnectorSelectedTransformer.notFound("No source column holds the information of this schema node");
        }
        ConnectorSelectedTransformer selected = selectForColumns(context, previewData, schemaNode, instructions, columns, nodeDTO);
        if (!selected.isFoundTransformer() && clear) {
            List<String> asked = selectColumns(context, previewData, schemaNode, instructions, candidates);
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
        validateTool.init(context, schemaNode, nodeDTO,
                TransformerHelper.valueRows(previewData, columns, context.fileProfiles(), patientIdColumn));
        writerTool.init(context, schemaNode, nodeDTO, previewData);

        if (columns.size() == 1) {
            ConnectorSelectedTransformer direct = ConnectorSelectedTransformer.mapping(columns.getFirst(), List.of());
            ConnectorValidationResultDTO validation = validateTool.validate(direct);
            if (validation.isValid()) {
                return direct;
            }
            hints += "\nMapping column '" + columns.getFirst() + "' directly is rejected: " + validation.getMessage();
        }

        List<String> keep = new ArrayList<>(columns);
        if (patientIdColumn != null && !keep.contains(patientIdColumn)) {
            keep.add(patientIdColumn);
        }
        ConnectorSelectedTransformer selected = runWorkflow(previewData.filterColumns(keep), schemaNode, columns,
                hints.strip(), TransformerHelper.describeFileValues(columns, context.fileProfiles()));
        if (!selected.isFoundTransformer()) {
            // A custom transformer written for this node but not taken is not worth keeping
            writerTool.discard();
        }
        return selected;
    }

    private ConnectorSelectedTransformer runWorkflow(TableSample selectedData, LocalSchemaNodeDTO schemaNode,
                                                     List<String> columns, String hints, String fileValues) {
        ConnectorSelectedTransformer selected;
        try {
            selected = transformerWorkflow.select(selectedData, schemaNode, columns, hints, fileValues);
        } catch (RuntimeException e) {
            Log.warnf(e, "Transformer selection for schema node %s failed", schemaNode.getName());
            return ConnectorSelectedTransformer.notFound("Transformer selection failed: " + e.getMessage());
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

    private List<String> selectColumns(TransformerRunContext context, TableSample previewData, LocalSchemaNodeDTO schemaNode,
                                       String hints, List<ColumnCandidate> candidates) {
        // A column that holds another node already is no candidate unless this node needs it as well
        List<ColumnCandidate> annotated = candidates.stream()
                .map(candidate -> context.mappedColumns().containsKey(candidate.getColumn())
                        ? candidate.withNote("already holds " + context.mappedColumns().get(candidate.getColumn())
                        + ", pick it only if this node is computed from it as well")
                        : candidate)
                .toList();
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
            return List.of();
        }
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
