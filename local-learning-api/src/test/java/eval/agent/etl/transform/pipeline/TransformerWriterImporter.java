package eval.agent.etl.transform.pipeline;

import bio.cosy.feddb.local.api.cohort.patient.PatientAO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryAO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.ImportStatusEnum;
import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogBO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
public class TransformerWriterImporter {

    private static final Duration TIMEOUT = Duration.ofMinutes(30);
    private static final Duration POLL = Duration.ofSeconds(5);

    @Inject
    ConnectorBO connectorBO;

    @Inject
    ConnectorRunBO connectorRunBO;

    @Inject
    ConnectorRunPatientLogBO patientLogBO;

    @Inject
    PatientAO patientAO;

    @Inject
    PatientDataEntryAO patientDataEntryAO;

    @Inject
    FLNetClientConfig config;

    public ConnectorRunDTO importWith(Long connectorId, ConnectorDTO suggested) {
        String keycloakId = config.user().systemUserName();
        saveSuggestion(connectorId, suggested, keycloakId);
        Log.infof("[ETL-Eval:import] Importing with %d transformers and %d mappings", suggested.getTransformer().size(),
                suggested.getSchemaMapping().size());
        return awaitRun(connectorRunBO.startRun(connectorId, true, false, keycloakId).getId());
    }

    @Transactional
    public String describe(ConnectorRunDTO run, Long cohortId) {
        return "%s: %d patients, %d data entries, %d failed%s".formatted(run.getStatus(),
                patientAO.countByCohortId(cohortId), run.getNewDataEntries(), run.getFailedDataEntries(),
                run.getErrorMessage() == null ? "" : " - " + run.getErrorMessage());
    }

    @Transactional
    public Map<String, Long> storedValues(Long cohortId, Long schemaId) {
        Map<String, Long> values = new HashMap<>();
        patientDataEntryAO.countValuesBySchemaNode(cohortId, schemaId)
                .forEach(row -> values.merge(storedValue(row), (Long) row[6], Long::sum));
        return values;
    }

    @Transactional
    public Map<String, Long> rejectedValues(ConnectorRunDTO run, LocalSchemaNodeDTO node, String mappedColumn) {
        List<String> fields = List.of(mappedColumn, node.getName(), String.valueOf(node.getId()));
        Map<String, Long> rejections = new HashMap<>();
        patientLogBO.getByRunId(run.getId(), null, null).stream()
                .filter(log -> log.getField() != null && fields.contains(log.getField()))
                .forEach(log -> rejections.merge(log.getMessage(), 1L, Long::sum));
        return rejections;
    }

    private void saveSuggestion(Long connectorId, ConnectorDTO suggested, String keycloakId) {
        for (int position = 0; position < suggested.getTransformer().size(); position++) {
            suggested.getTransformer().get(position).setPosition(position);
        }
        QuarkusTransaction.requiringNew().run(() -> {
            ConnectorDTO connector = connectorBO.getById(connectorId);
            connector.setTransformer(suggested.getTransformer());
            connector.setSchemaMapping(suggested.getSchemaMapping());
            connectorBO.update(connectorId, connector, keycloakId);
        });
    }

    private ConnectorRunDTO awaitRun(Long runId) {
        Instant deadline = Instant.now().plus(TIMEOUT);
        ConnectorRunDTO run = loadRun(runId);
        while (!isDone(run) && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(POLL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return run;
            }
            run = loadRun(runId);
        }
        return run;
    }

    private ConnectorRunDTO loadRun(Long runId) {
        return QuarkusTransaction.requiringNew().call(() -> connectorRunBO.getById(runId));
    }

    private static boolean isDone(ConnectorRunDTO run) {
        return run.getStatus() == ImportStatusEnum.FINISHED || run.getStatus() == ImportStatusEnum.ERROR;
    }

    private static String storedValue(Object[] row) {
        Object value = Arrays.stream(row, 0, 6).filter(Objects::nonNull).findFirst().orElse(null);
        return value == null ? "(empty)" : "'" + value + "'";
    }
}
