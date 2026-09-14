package bio.cosy.feddb.local.api.importer.run;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientGroupResultDTO {
    private long rowCount;
    private Long currentTransformingStep;
    private long newEntities;
    private long updatedEntities;
    private long deletedEntities;
    private long failedEntities;
    private long unchangedEntities;
    private long receivedEntities;
    private long processedEntities;
    private long newDataEntries;
    private long failedDataEntries;

    public static PatientGroupResultDTO from(ConnectorRunDTO worker, long rowCount) {
        PatientGroupResultDTO result = new PatientGroupResultDTO();
        result.setRowCount(rowCount);
        result.setCurrentTransformingStep(worker.getCurrentTransformingStep());
        result.setNewEntities(orZero(worker.getNewEntities()));
        result.setUpdatedEntities(orZero(worker.getUpdatedEntities()));
        result.setDeletedEntities(orZero(worker.getDeletedEntities()));
        result.setFailedEntities(orZero(worker.getFailedEntities()));
        result.setUnchangedEntities(orZero(worker.getUnchangedEntities()));
        result.setReceivedEntities(orZero(worker.getReceivedEntities()));
        result.setProcessedEntities(orZero(worker.getProcessedEntities()));
        result.setNewDataEntries(orZero(worker.getNewDataEntries()));
        result.setFailedDataEntries(orZero(worker.getFailedDataEntries()));
        return result;
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
