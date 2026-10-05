package bio.cosy.feddb.local.agent.etl.transform.organizer;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.agents.ColumnSelectAgent;
import bio.cosy.feddb.local.agent.etl.transform.agents.TransformerWorkflow;
import bio.cosy.feddb.local.agent.etl.transform.model.ColumnCandidate;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerUserTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerValidateTool;
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
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

@ApplicationScoped
public class TransformerOrganizer {

    @Inject
    ColumnSelectAgent columnSelectAgent;

    @Inject
    TransformerWorkflow transformerWorkflow;

    @Inject
    TransformerValidateTool validateTool;

    @Inject
    TransformerUserTool userTool;

    @Inject
    DataTypeEntityMapper dataTypeMapper;

    @ActivateRequestContext
    public ConnectorSelectedTransformer selectTransformer(TableSample previewData,
                                                          LocalSchemaNodeDTO schemaNode,
                                                          String patientIdColumn,
                                                          String instructions,
                                                          UnaryOperator<String> askUser,
                                                          List<ColumnCandidate> candidates,
                                                          Map<String, ColumnProfile> fileProfiles,
                                                          DataTypeDTO dataType) {

        DataTypeNodeDTO nodeDTO = dataTypeMapper.dtoToNodeDto(dataType);

        validateTool.init(previewData, schemaNode, patientIdColumn, fileProfiles, nodeDTO);
        userTool.init(askUser);
        String hints = instructions;

        List<String> columns = selectColumns(previewData, schemaNode, hints, candidates);
        if (columns.isEmpty()) {
            return ConnectorSelectedTransformer.notFound("No source column holds the information of this schema node");
        }

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
        ConnectorSelectedTransformer selected;
        try {
            selected = transformerWorkflow.select(previewData.filterColumns(keep), schemaNode, columns, hints.strip());
            if (selected == null) {
                return ConnectorSelectedTransformer.notFound("No transformer found");
            }
        } catch (RuntimeException e) {
            Log.warnf(e, "Transformer selection for schema node %s failed", schemaNode.getName());
            return ConnectorSelectedTransformer.notFound("Transformer selection failed: " + e.getMessage());
        }
        if (!selected.isFoundTransformer()) {
            return selected;
        }
        ConnectorValidationResultDTO validation = validateTool.validate(selected);
        if (validation.isValid()) {
            return selected;
        }
        selected.setFoundTransformer(false);
        selected.setNeededTransformerFunction(validation.getMessage());
        return selected;
    }

    private List<String> selectColumns(TableSample previewData, LocalSchemaNodeDTO schemaNode, String hints,
                                       List<ColumnCandidate> candidates) {
        ColumnCandidate best = candidates.isEmpty() ? null : candidates.getFirst();
        if (best != null && (best.isDirect() || best.getNameScore() == 1)) {
            return List.of(best.getColumn());
        }
        try {
            List<String> selected = columnSelectAgent.select(previewData, schemaNode, hints.strip(), candidates);
            return selected == null ? List.of() : selected.stream()
                    .filter(previewData.getColumns()::contains)
                    .distinct()
                    .toList();
        } catch (RuntimeException e) {
            Log.warnf(e, "Column selection for schema node %s failed", schemaNode.getName());
            return List.of();
        }
    }
}
