package bio.cosy.feddb.local.api.importer.transformer;

import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import bio.cosy.feddb.local.api.importer.functions.FunctionRunnerBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.transformer.session.AppTransformerSessionBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
public class ConnectorTransformationBO {

    @Inject
    FunctionRunnerBO functionRunnerBO;

    @Inject
    AppTransformerSessionBO sessionBO;

    public List<Map<String, Object>> applyTransformations(ConnectorDTO connector, ConnectorRunDTO run,
                                                         List<Map<String, Object>> rows) {
        Objects.requireNonNull(connector, "connector must not be null");
        Objects.requireNonNull(run, "run must not be null");
        List<ConnectorTransformerDTO> transformers = connector.getTransformer() == null ? List.of() : connector.getTransformer();
        for (int index = 0; index < transformers.size(); index++) {
            run.setCurrentTransformingStep((long) index + 1);
            rows = applyTransformation(transformers.get(index), run, rows);
        }
        return rows;
    }

    // An app runs on the container the session started for the whole run, every batch is another run on it
    public List<Map<String, Object>> applyTransformation(ConnectorTransformerDTO transformer, ConnectorRunDTO run,
                                                         List<Map<String, Object>> rows) {
        Objects.requireNonNull(transformer, "transformer must not be null");
        Objects.requireNonNull(run, "run must not be null");
        if (transformer.isAppBased()) {
            return sessionBO.transform(run.getId(), transformer, rows);
        }
        String cohortId = run.getCohortId() == null ? null : String.valueOf(run.getCohortId());
        return functionRunnerBO.apply(transformer, rows, cohortId, run.getId());
    }

    // Used by the preview and the statistics, which run without the containers of an import
    public List<Map<String, Object>> applyBuiltInTransformations(List<ConnectorTransformerDTO> transformers,
                                                                 List<Map<String, Object>> rows,
                                                                 String patientIdColumn) {
        for (ConnectorTransformerDTO transformer : transformers) {
            rows = applyBuiltInTransformation(transformer, rows, patientIdColumn);
        }
        return rows;
    }

    public List<Map<String, Object>> applyBuiltInTransformation(ConnectorTransformerDTO transformer,
                                                                List<Map<String, Object>> rows,
                                                                String patientIdColumn) {
        Objects.requireNonNull(transformer, "transformer must not be null");
        if (transformer.isAppBased()) {
            Log.infof("Skipping app-based transformer '%s'; it requires the execution subsystem", transformer.getMethodName());
            return rows;
        }
        return functionRunnerBO.mode(transformer) == FunctionExecutionMode.PATIENT
                ? applyPatientTransformation(transformer, rows, patientIdColumn)
                : applyFunction(transformer, rows);
    }

    public List<Map<String, Object>> applyPatientTransformation(ConnectorTransformerDTO transformer,
                                                                List<Map<String, Object>> rows,
                                                                String patientIdColumn) {
        if (patientIdColumn == null || patientIdColumn.isBlank()) {
            throw new BadRequestException("Patient transformations require a source column mapped to the external patient id");
        }
        List<Map<String, Object>> transformed = new ArrayList<>(rows.size());
        groupByPatient(rows, patientIdColumn).values()
                .forEach(patientRows -> transformed.addAll(applyFunction(transformer, patientRows)));
        return transformed;
    }

    private List<Map<String, Object>> applyFunction(ConnectorTransformerDTO transformer, List<Map<String, Object>> rows) {
        return functionRunnerBO.apply(transformer, rows, null, null);
    }

    private static Map<String, List<Map<String, Object>>> groupByPatient(List<Map<String, Object>> rows,
                                                                        String patientIdColumn) {
        Map<String, List<Map<String, Object>>> rowsByPatient = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            rowsByPatient.computeIfAbsent(patientIdOf(row, patientIdColumn), patientId -> new ArrayList<>()).add(row);
        }
        return rowsByPatient;
    }

    private static String patientIdOf(Map<String, Object> row, String patientIdColumn) {
        if (row == null) {
            throw new BadRequestException("Encountered a null patient row");
        }
        Object patientId = row.get(patientIdColumn);
        if (patientId == null || String.valueOf(patientId).isBlank()) {
            throw new BadRequestException("Row is missing the external patient id in column '" + patientIdColumn + "'");
        }
        return String.valueOf(patientId);
    }
}
