package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.core.base.BaseAuthDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConnectorRunDTO extends BaseAuthDTO {

    private Long progress = 0L;
    private ImportStatusEnum status = ImportStatusEnum.INIT;
    private ConnectorRunStep currentStep = ConnectorRunStep.INIT;
    private Boolean deleteExistingPatients = false;
    private Boolean dryRun = false;
    private Long expectedElements; // total number of distinct patients to process

    private Long progressExtracting; // rows loaded during extraction
    private Long currentTransformingStep;
    private Long currentElementNr; // 1-based index of the patient currently being processed
    private Long currentRowNr; // cumulative number of rows processed so far

    private Long newEntities = 0L;
    private Long updatedEntities = 0L;
    private Long deletedEntities = 0L;
    private Long failedEntities = 0L;
    private Long unchangedEntities = 0L;
    private Long receivedEntities = 0L;
    private Long processedEntities = 0L;
    private Long newDataEntries = 0L;
    private Long failedDataEntries = 0L;

    private String errorMessage;

    private Long cohortId;
    private Long connectorId;

    /** Resets all mutable execution state before this run enters the ETL pipeline. */
    @JsonIgnore
    public void initializeForImport() {
        progress = 0L;
        status = ImportStatusEnum.INIT;
        currentStep = ConnectorRunStep.INIT;
        expectedElements = 0L;
        progressExtracting = 0L;
        currentTransformingStep = 0L;
        currentElementNr = 0L;
        currentRowNr = 0L;
        newEntities = 0L;
        updatedEntities = 0L;
        deletedEntities = 0L;
        failedEntities = 0L;
        unchangedEntities = 0L;
        receivedEntities = 0L;
        processedEntities = 0L;
        newDataEntries = 0L;
        failedDataEntries = 0L;
        errorMessage = null;
    }

    /** Creates isolated, zero-based statistics for one parallel patient worker. */
    @JsonIgnore
    public ConnectorRunDTO createPatientWorker() {
        ConnectorRunDTO worker = new ConnectorRunDTO();
        worker.setId(getId());
        worker.setKeycloakId(getKeycloakId());
        worker.setCohortId(cohortId);
        worker.setConnectorId(connectorId);
        worker.setDeleteExistingPatients(deleteExistingPatients);
        worker.setDryRun(dryRun);
        return worker;
    }

    @JsonIgnore
    public void markPatientProcessed() {
        receivedEntities = 1L;
        processedEntities = 1L;
    }

    @JsonIgnore
    public void markPatientUnchanged() {
        markPatientProcessed();
        newEntities = 0L;
        updatedEntities = 0L;
        failedEntities = 0L;
        unchangedEntities = 1L;
        newDataEntries = 0L;
    }

    @JsonIgnore
    public void markPatientFailed() {
        markPatientProcessed();
        newEntities = 0L;
        updatedEntities = 0L;
        failedEntities = 1L;
        unchangedEntities = 0L;
        newDataEntries = 0L;
    }

    /** Atomically adds the completed statistics of one patient group to this run. */
    @JsonIgnore
    public void addPatientGroupResult(PatientGroupResultDTO result) {
        long nextCurrentElementNr = addCounter(currentElementNr, 1L, "currentElementNr");
        long nextCurrentRowNr = addCounter(currentRowNr, result.getRowCount(), "currentRowNr");
        long nextNewEntities = addCounter(newEntities, result.getNewEntities(), "newEntities");
        long nextUpdatedEntities = addCounter(updatedEntities, result.getUpdatedEntities(), "updatedEntities");
        long nextDeletedEntities = addCounter(deletedEntities, result.getDeletedEntities(), "deletedEntities");
        long nextFailedEntities = addCounter(failedEntities, result.getFailedEntities(), "failedEntities");
        long nextUnchangedEntities = addCounter(
                unchangedEntities,
                result.getUnchangedEntities(),
                "unchangedEntities"
        );
        long nextReceivedEntities = addCounter(receivedEntities, result.getReceivedEntities(), "receivedEntities");
        long nextProcessedEntities = addCounter(
                processedEntities,
                result.getProcessedEntities(),
                "processedEntities"
        );
        long nextNewDataEntries = addCounter(newDataEntries, result.getNewDataEntries(), "newDataEntries");
        long nextFailedDataEntries = addCounter(
                failedDataEntries,
                result.getFailedDataEntries(),
                "failedDataEntries"
        );

        currentElementNr = nextCurrentElementNr;
        currentRowNr = nextCurrentRowNr;
        currentStep = ConnectorRunStep.LOADING;
        if (result.getCurrentTransformingStep() != null) {
            currentTransformingStep = result.getCurrentTransformingStep();
        }
        newEntities = nextNewEntities;
        updatedEntities = nextUpdatedEntities;
        deletedEntities = nextDeletedEntities;
        failedEntities = nextFailedEntities;
        unchangedEntities = nextUnchangedEntities;
        receivedEntities = nextReceivedEntities;
        processedEntities = nextProcessedEntities;
        newDataEntries = nextNewDataEntries;
        failedDataEntries = nextFailedDataEntries;
    }

    private static long addCounter(Long current, long delta, String name) {
        if (delta < 0L) {
            throw new IllegalArgumentException(name + " delta must not be negative: " + delta);
        }
        return Math.addExact(orZero(current), delta);
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
