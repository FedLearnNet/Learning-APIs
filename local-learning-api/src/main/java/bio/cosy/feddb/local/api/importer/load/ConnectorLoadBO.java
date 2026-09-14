package bio.cosy.feddb.local.api.importer.load;

import bio.cosy.feddb.local.api.cohort.patient.traceability.crud.AuditContext;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.mapping.MappingRowResultDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class ConnectorLoadBO {

    @Inject
    ConnectorLoadPatientBO connectorLoadPatientBO;

    @Inject
    Instance<AuditContext> auditContextInstance;

    /**
     * Loads the rows of a single patient group. Run progress counters and {@code expectedElements}
     * are owned by the streaming ETL pipeline ({@code ConnectorRunETLBO}); this method only performs
     * the per-patient persistence and lets {@link ConnectorLoadPatientBO#importPatient} mutate the
     * in-memory entity counters.
     */
    public void loadPatient(ConnectorDTO connectorDTO, ConnectorRunDTO run, List<MappingRowResultDTO> patientRows) {
        assert Objects.equals(connectorDTO.getId(), run.getConnectorId());
        assert run.getCohortId() != null && run.getCohortId().equals(connectorDTO.getCohortId());

        setAuditContext(run.getKeycloakId(), connectorDTO.getId(), run.getId());
        connectorLoadPatientBO.importPatient(requireSinglePatient(patientRows), run);
    }

    public void validateSinglePatient(List<MappingRowResultDTO> patientRows) {
        requireSinglePatient(patientRows);
    }

    private ConnectorLoadPatient requireSinglePatient(List<MappingRowResultDTO> patientRows) {
        if (patientRows == null || patientRows.isEmpty()) {
            throw new IllegalArgumentException("A patient group must contain at least one mapped row");
        }

        String externalPatientId = null;
        List<ConnectorLoadRow> rows = new ArrayList<>(patientRows.size());
        for (MappingRowResultDTO mappedRow : patientRows) {
            if (mappedRow == null) {
                throw new IllegalArgumentException("A patient group must not contain null mapped rows");
            }
            String rowPatientId = mappedRow.getExternalPatientId();
            if (rowPatientId == null || rowPatientId.isBlank()) {
                throw new IllegalArgumentException("Every mapped row must have an external patient id");
            }
            if (externalPatientId == null) {
                externalPatientId = rowPatientId;
            } else if (!externalPatientId.equals(rowPatientId)) {
                throw new IllegalArgumentException(
                        "A patient group mapped to multiple external patient ids: '"
                                + externalPatientId + "' and '" + rowPatientId + "'"
                );
            }
            rows.add(toLoadRow(mappedRow));
        }

        ConnectorLoadPatient patient = new ConnectorLoadPatient();
        patient.setExternalPatientId(externalPatientId);
        patient.setRows(rows);
        return patient;
    }

    private void setAuditContext(String keycloakId, Long connectorId, Long runId) {
        if (Arc.container() == null || !Arc.container().requestContext().isActive()) {
            Log.debug("Request context is not active, using thread-local audit context for WebSocket request.");
            return;
        }
        AuditContext auditContext = auditContextInstance.isResolvable() ? auditContextInstance.get() : null;
        if (auditContext == null) {
            Log.error("AuditContext is null, cannot set audit context for WebSocket request.");
            return;
        }
        auditContext.setKeycloakId(keycloakId);
        auditContext.setConnectorId(connectorId);
        auditContext.setRunId(runId);
    }

    private ConnectorLoadRow toLoadRow(@Nonnull MappingRowResultDTO mappedRow) {
        ConnectorLoadRow row = new ConnectorLoadRow();
        row.setEntries(
                mappedRow.getEntries() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(mappedRow.getEntries())
        );
        row.setValidationResult(
                mappedRow.getValidationResult() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(mappedRow.getValidationResult())
        );
        return row;
    }
}
