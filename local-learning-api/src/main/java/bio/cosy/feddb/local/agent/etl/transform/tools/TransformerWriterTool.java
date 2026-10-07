package bio.cosy.feddb.local.agent.etl.transform.tools;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.local.agent.etl.transform.model.SavedCodeTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.TransformerRunContext;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerBO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerStatus;
import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckResultDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerContainerBO;
import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerRequestBO;
import bio.cosy.feddb.local.api.importer.transformer.code.save.CodeTransformerSaveDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

@RequestScoped
public class TransformerWriterTool {

    static final Duration BUILD_TIMEOUT = Duration.ofMinutes(10);
    static final Duration BUILD_POLL = Duration.ofSeconds(3);

    @Inject
    CodeTransformerContainerBO containerBO;

    @Inject
    CodeTransformerRequestBO requestBO;

    @Inject
    CodeTransformerBO codeTransformerBO;

    private TransformerRunContext context;
    private LocalSchemaNodeDTO schemaNode;
    private DataTypeNodeDTO goal;
    private TableSample sample;
    private CodeTransformerCheckDTO checked;
    private List<String> checkedColumns;
    private CodeTransformerDTO saved;

    public void init(TransformerRunContext context, LocalSchemaNodeDTO schemaNode, DataTypeNodeDTO goal, TableSample sample) {
        this.context = context;
        this.schemaNode = schemaNode;
        this.goal = goal;
        this.sample = sample;
        this.checked = null;
        this.checkedColumns = null;
        this.saved = null;
    }

    @Tool("""
            Run the body of a custom Python transformer on the preview rows of the input columns and check every result
            against the data type of the schema node. The first call starts the code runner, which takes a moment.
            Returns whether all results are valid, the keys the code reads and returns, and samples, failed ones first.
            """)
    public CodeTransformerCheckResultDTO checkCode(
            @P("CELL, ROW or PATIENT") FunctionExecutionMode mode,
            @P("The source columns the code reads, exactly one for CELL") List<String> inputColumns,
            @P("The body of transform, without the def line") String body) {
        checked = null;
        String rejected = rejectInput(mode, inputColumns);
        if (rejected != null) {
            return CodeTransformerCheckResultDTO.failed(null, rejected);
        }
        try {
            containerBO.start(context.keycloakId());
        } catch (RuntimeException e) {
            return CodeTransformerCheckResultDTO.failed(null, "The code runner could not be started: " + e.getMessage());
        }
        CodeTransformerCheckDTO request = new CodeTransformerCheckDTO();
        request.setMode(mode);
        request.setBody(body);
        request.setGoal(goal);
        request.setData(data(mode, inputColumns));
        CodeTransformerCheckResultDTO result = requestBO.check(request, context.keycloakId());
        if (Boolean.TRUE.equals(result.getIsSuccess())) {
            checked = request;
            checkedColumns = List.copyOf(inputColumns);
        }
        return result;
    }

    @Tool("""
            Save the code of the last checkCode, which must have succeeded, as custom transformer and build its image,
            which takes a few minutes. Saving again updates the same transformer. Returns the transformer configured
            for the checked columns and the column it writes.
            """)
    public SavedCodeTransformer saveCode(
            @P("A short name saying what the code does in general, e.g. 'Parse German dates'") String name,
            @P("What the code expects and returns") String description) {
        if (checked == null) {
            return SavedCodeTransformer.failed("Run checkCode until it succeeds before saving");
        }
        CodeTransformerSaveDTO request = new CodeTransformerSaveDTO();
        request.setId(saved == null ? null : saved.getId());
        request.setName(name);
        request.setDescription(description == null ? "" : description);
        request.setMode(checked.getMode());
        request.setBody(checked.getBody());
        request.setGoal(checked.getGoal());
        request.setData(checked.getData());
        try {
            saved = codeTransformerBO.save(request, context.keycloakId());
        } catch (RuntimeException e) {
            return SavedCodeTransformer.failed("Saving failed: " + e.getMessage());
        }
        saved = awaitBuild(saved);
        if (saved.getStatus() != CodeTransformerStatus.READY) {
            return SavedCodeTransformer.failed(saved.getStatus() == CodeTransformerStatus.BUILDING
                    ? "The image was not built within " + BUILD_TIMEOUT.toMinutes() + " minutes"
                    : "Building the image failed");
        }
        return configure(saved);
    }

    @Tool("The code saved in a previous attempt for this schema node, to continue from it")
    public String getSavedCode() {
        if (saved == null) {
            return "Nothing saved yet";
        }
        return "Name: " + saved.getName() + "\nMode: " + saved.getMode() + "\nInput columns: " + saved.getData().keySet()
                + "\nBody:\n" + saved.getBody();
    }

    public void discard() {
        if (saved == null) {
            return;
        }
        try {
            codeTransformerBO.delete(saved.getId());
        } catch (RuntimeException e) {
            Log.infof("Keeping the custom transformer %s: %s", saved.getName(), e.getMessage());
        }
        saved = null;
    }

    private String rejectInput(FunctionExecutionMode mode, List<String> inputColumns) {
        if (mode == null || inputColumns == null || inputColumns.isEmpty()) {
            return "Pass the mode and the source columns the code reads";
        }
        List<String> unknown = inputColumns.stream().filter(column -> !sample.getColumns().contains(column)).toList();
        if (!unknown.isEmpty()) {
            return "Unknown columns " + unknown + ", the columns are " + sample.getColumns();
        }
        if (mode == FunctionExecutionMode.CELL && inputColumns.size() > 1) {
            return "A CELL transformer reads exactly one column, use ROW to combine several";
        }
        if (mode == FunctionExecutionMode.PATIENT && context.patientIdColumn() == null) {
            return "PATIENT needs the patient id column, which is not known; use ROW";
        }
        return null;
    }

    private Map<String, List<Object>> data(FunctionExecutionMode mode, List<String> inputColumns) {
        List<Map<String, Object>> rows = sample.getRows().stream().filter(Objects::nonNull).toList();
        if (mode == FunctionExecutionMode.PATIENT) {
            String patientIdColumn = context.patientIdColumn();
            rows = rows.stream()
                    .filter(row -> row.get(patientIdColumn) != null)
                    .collect(Collectors.groupingBy(row -> row.get(patientIdColumn), LinkedHashMap::new, Collectors.toList()))
                    .values().stream()
                    .max(Comparator.comparingInt(List::size))
                    .orElse(rows);
        }
        Map<String, List<Object>> data = new LinkedHashMap<>();
        for (String column : inputColumns) {
            data.put(column, rows.stream().map(row -> row.get(column)).toList());
        }
        return data;
    }

    private CodeTransformerDTO awaitBuild(CodeTransformerDTO building) {
        Instant deadline = Instant.now().plus(BUILD_TIMEOUT);
        CodeTransformerDTO current = building;
        while (current.getStatus() == CodeTransformerStatus.BUILDING && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(BUILD_POLL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Stopped while building the custom transformer " + building.getName());
            }
            current = QuarkusTransaction.requiringNew().call(() -> codeTransformerBO.getById(building.getId()));
        }
        return current;
    }

    private SavedCodeTransformer configure(CodeTransformerDTO code) {
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        String mappedColumn;
        if (code.getMode() == FunctionExecutionMode.CELL) {
            mappedColumn = checkedColumns.getFirst();
            transformer.setColumn(mappedColumn);
        } else {
            mappedColumn = sample.getColumns().contains(schemaNode.getName())
                    ? schemaNode.getName() + "_" + schemaNode.getId()
                    : schemaNode.getName();
            Map<String, Object> inputMapping = new LinkedHashMap<>();
            checkedColumns.forEach(column -> inputMapping.put(column, column));
            transformer.setInputMapping(inputMapping);
            String returnKey = code.getReturnKeys() == null || code.getReturnKeys().isEmpty()
                    ? TransformerListTool.CODE_RETURN_KEY
                    : code.getReturnKeys().getFirst();
            transformer.setReturnMapping(new LinkedHashMap<>(Map.of(returnKey, mappedColumn)));
        }
        TransformerListTool.configure(transformer, code, context.patientIdColumn());
        return new SavedCodeTransformer(true, "Saved and built '" + code.getName() + "'", transformer, mappedColumn);
    }
}
