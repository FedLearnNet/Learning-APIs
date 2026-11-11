package bio.cosy.feddb.local.api.importer.transformer;

import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorHelper;
import bio.cosy.feddb.local.api.importer.files.table.TableDataGroup;
import bio.cosy.feddb.local.api.importer.run.AppTransformerSessionFailure;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Batches only remote stages. Built-ins, failures, mapping and loads retain patient boundaries. */
@ApplicationScoped
public class ConnectorTransformerBatchBO {
    @Inject
    ConnectorTransformerBO transformerBO;

    @Inject
    AppTransformerSessionBO sessionBO;

    @Inject
    AppTransformerBatchBO batchBO;

    public record PatientResult(TableDataGroup source, List<Map<String, Object>> rows,
                                Long step, RuntimeException failure) {
    }

    public List<PatientResult> transform(ConnectorDTO connector, ConnectorRunDTO run,
                                          List<TableDataGroup> groups, String patientColumn) throws Exception {
        List<PatientResult> patients = groups.stream()
                .map(group -> new PatientResult(group, new ArrayList<>(group.rows()), null, null))
                .toList();
        List<ConnectorTransformerDTO> stages = connector.getTransformer();
        for (int index = 0; index < stages.size(); index++) {
            ConnectorTransformerDTO stage = stages.get(index);
            long step = index + 1L;
            if (stage.isAppBased()) {
                patients = applyApp(stage, run, patients, patientColumn, step);
            } else {
                List<PatientResult> next = new ArrayList<>(patients.size());
                for (PatientResult patient : patients) {
                    if (patient.failure() != null || patient.rows().isEmpty()) {
                        next.add(patient);
                        continue;
                    }
                    ConnectorRunDTO worker = run.createPatientWorker();
                    worker.setCurrentTransformingStep(step);
                    try {
                        List<Map<String, Object>> rows = transformerBO.applyTransformation(stage, worker, patient.rows());
                        if (rows == null) {
                            throw new IllegalStateException("Transformation returned null");
                        }
                        next.add(new PatientResult(patient.source(), rows, step, null));
                    } catch (RuntimeException e) {
                        next.add(new PatientResult(patient.source(), patient.rows(), step, e));
                    }
                }
                patients = next;
            }
        }
        return patients;
    }

    private List<PatientResult> applyApp(ConnectorTransformerDTO stage, ConnectorRunDTO run,
                                         List<PatientResult> patients, String patientColumn, long step) throws Exception {
        Map<String, PatientResult> active = new LinkedHashMap<>();
        for (PatientResult patient : patients) {
            if (patient.failure() == null && !patient.rows().isEmpty()) {
                active.put(patient.source().key(), patient);
            }
        }
        Iterator<PatientResult> iterator = active.values().iterator();
        // A built-in may intentionally rewrite/remove the external id. Such rows retain their
        // original patient worker, but cannot safely be regrouped from multi-patient app output.
        boolean identifiable = active.values().stream().allMatch(patient -> patient.rows().stream()
                .allMatch(row -> patient.source().key().equals(patientId(row, patientColumn))));
        AppTransformerBatchBO.Reader reader = batchBO.reader(() -> {
            if (!iterator.hasNext()) {
                return null;
            }
            PatientResult patient = iterator.next();
            return new TableDataGroup(patient.source().key(), patient.rows());
        }, stage, identifiable);
        Map<String, List<Map<String, Object>>> transformed = new LinkedHashMap<>();
        List<TableDataGroup> batch;
        while (!(batch = reader.nextBatch()).isEmpty()) {
            List<Map<String, Object>> input = new ArrayList<>();
            for (TableDataGroup patient : batch) {
                for (Map<String, Object> row : patient.rows()) {
                    if (batch.size() > 1 && !patient.key().equals(patientId(row, patientColumn))) {
                        throw new AppTransformerSessionFailure("App transformer '" + stage.getAppImage()
                                + "' cannot batch rows whose patient id column '" + patientColumn
                                + "' was removed or changed by an earlier step");
                    }
                    input.add(row);
                }
            }
            List<Map<String, Object>> output = sessionBO.transformBatch(run.getId(), stage, input);
            transformed.putAll(splitOutput(batch, output, patientColumn, stage));
        }
        return patients.stream().map(patient -> transformed.containsKey(patient.source().key())
                ? new PatientResult(patient.source(), transformed.get(patient.source().key()), step, null)
                : patient).toList();
    }

    static Map<String, List<Map<String, Object>>> splitOutput(List<TableDataGroup> patients,
            List<Map<String, Object>> output, String patientColumn, ConnectorTransformerDTO stage) {
        Map<String, List<Map<String, Object>>> byPatient = new LinkedHashMap<>();
        patients.forEach(patient -> byPatient.put(patient.key(), new ArrayList<>()));
        // Preserve support for older tools returning only rewritten columns when explicitly run alone.
        if (patients.size() == 1) {
            byPatient.put(patients.getFirst().key(), ConnectorHelper.mergeRows(patients.getFirst().rows(), output, stage));
            return byPatient;
        }
        for (Map<String, Object> row : output) {
            String key = patientId(row, patientColumn);
            if (key == null || !byPatient.containsKey(key)) {
                throw new AppTransformerSessionFailure("App transformer '" + stage.getAppImage()
                        + "' returned a missing or unexpected patient id in column '" + patientColumn
                        + "'. Batched tools must preserve patient ids; use app-transform-batch-patients=1 for legacy tools.");
            }
            byPatient.get(key).add(row);
        }
        // Never merge different patients by position. Within a patient this retains the existing
        // partial-column output contract; standard Tool API transformers return complete rows.
        patients.forEach(patient -> byPatient.put(patient.key(),
                ConnectorHelper.mergeRows(patient.rows(), byPatient.get(patient.key()), stage)));
        return byPatient;
    }

    private static String patientId(Map<String, Object> row, String column) {
        Object value = row == null ? null : row.get(column);
        return value == null ? null : String.valueOf(value);
    }
}
