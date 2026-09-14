package unit.importer;

import bio.cosy.feddb.local.api.importer.load.ConnectorLoadBO;
import bio.cosy.feddb.local.api.importer.mapping.MappingRowResultDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunStep;
import bio.cosy.feddb.local.api.importer.run.ImportStatusEnum;
import bio.cosy.feddb.local.api.importer.run.PatientGroupResultDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectorRunStatisticsTest {

    @Test
    void initializationResetsAllExecutionState() {
        ConnectorRunDTO run = new ConnectorRunDTO();
        run.setProgress(73L);
        run.setStatus(ImportStatusEnum.ERROR);
        run.setCurrentStep(ConnectorRunStep.LOADING);
        run.setExpectedElements(100L);
        run.setNewEntities(20L);
        run.setFailedEntities(3L);
        run.setErrorMessage("old error");

        run.initializeForImport();

        assertEquals(0L, run.getProgress());
        assertEquals(ImportStatusEnum.INIT, run.getStatus());
        assertEquals(ConnectorRunStep.INIT, run.getCurrentStep());
        assertEquals(0L, run.getExpectedElements());
        assertEquals(0L, run.getNewEntities());
        assertEquals(0L, run.getFailedEntities());
        assertNull(run.getErrorMessage());
    }

    @Test
    void patientOutcomeMethodsProduceOneExclusiveOutcome() {
        ConnectorRunDTO worker = new ConnectorRunDTO();
        worker.setNewEntities(1L);
        worker.markPatientFailed();

        assertEquals(1L, worker.getReceivedEntities());
        assertEquals(1L, worker.getProcessedEntities());
        assertEquals(0L, worker.getNewEntities());
        assertEquals(1L, worker.getFailedEntities());
        assertEquals(0L, worker.getUnchangedEntities());

        worker.markPatientUnchanged();

        assertEquals(0L, worker.getFailedEntities());
        assertEquals(1L, worker.getUnchangedEntities());
    }

    @Test
    void patientWorkerCopiesContextButStartsWithZeroStatistics() {
        ConnectorRunDTO master = new ConnectorRunDTO();
        master.setId(7L);
        master.setKeycloakId("user");
        master.setCohortId(11L);
        master.setConnectorId(13L);
        master.setNewEntities(1_000L);
        master.setReceivedEntities(1_000L);

        ConnectorRunDTO worker = master.createPatientWorker();

        assertEquals(7L, worker.getId());
        assertEquals("user", worker.getKeycloakId());
        assertEquals(11L, worker.getCohortId());
        assertEquals(13L, worker.getConnectorId());
        assertEquals(0L, worker.getNewEntities());
        assertEquals(0L, worker.getReceivedEntities());
    }

    @Test
    void aggregationAddsOnlyPatientDeltas() {
        ConnectorRunDTO master = new ConnectorRunDTO();
        master.setCurrentElementNr(10L);
        master.setCurrentRowNr(100L);
        master.setNewEntities(10L);
        master.setReceivedEntities(10L);
        master.setProcessedEntities(10L);

        ConnectorRunDTO worker = master.createPatientWorker();
        worker.setNewEntities(1L);
        worker.setReceivedEntities(1L);
        worker.setProcessedEntities(1L);

        master.addPatientGroupResult(PatientGroupResultDTO.from(worker, 4L));

        assertEquals(11L, master.getCurrentElementNr());
        assertEquals(104L, master.getCurrentRowNr());
        assertEquals(11L, master.getNewEntities());
        assertEquals(11L, master.getReceivedEntities());
        assertEquals(11L, master.getProcessedEntities());
    }

    @Test
    void aggregationRejectsOverflowAndNegativeDeltas() {
        ConnectorRunDTO master = new ConnectorRunDTO();
        master.setCurrentElementNr(5L);
        master.setNewEntities(Long.MAX_VALUE);

        PatientGroupResultDTO overflow = new PatientGroupResultDTO();
        overflow.setNewEntities(1L);
        assertThrows(ArithmeticException.class, () -> master.addPatientGroupResult(overflow));
        assertEquals(5L, master.getCurrentElementNr(), "aggregation must be atomic");
        assertEquals(Long.MAX_VALUE, master.getNewEntities());

        ConnectorRunDTO freshMaster = new ConnectorRunDTO();
        PatientGroupResultDTO negative = new PatientGroupResultDTO();
        negative.setFailedEntities(-1L);
        assertThrows(IllegalArgumentException.class, () -> freshMaster.addPatientGroupResult(negative));
    }

    @Test
    void patientLoadValidationRequiresExactlyOneMappedPatient() {
        ConnectorLoadBO loadBO = new ConnectorLoadBO();

        assertDoesNotThrow(() -> loadBO.validateSinglePatient(List.of(
                mappedRow("patient-1"),
                mappedRow("patient-1")
        )));
        assertThrows(IllegalArgumentException.class, () -> loadBO.validateSinglePatient(List.of()));
        assertThrows(IllegalArgumentException.class, () -> loadBO.validateSinglePatient(List.of(
                mappedRow("patient-1"),
                mappedRow("patient-2")
        )));
        assertThrows(IllegalArgumentException.class, () -> loadBO.validateSinglePatient(List.of(
                mappedRow("patient-1"),
                mappedRow(null)
        )));
    }

    private MappingRowResultDTO mappedRow(String patientId) {
        MappingRowResultDTO row = new MappingRowResultDTO();
        row.setExternalPatientId(patientId);
        return row;
    }
}
