package bio.cosy.feddb.local.api.importer.run;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class ConnectorRunAO implements PanacheRepository<ConnectorRunEntity> {

    public List<ConnectorRunEntity> getAllForConnector(Long connectorId) {
    	return list("connector.id = ?1 ORDER BY createdAt DESC", connectorId);
    }

    public boolean hasRunningProcess(Long cohortId) {
        return count("cohort.id = ?1 and status in ?2", cohortId, List.of(ImportStatusEnum.INIT, ImportStatusEnum.RUNNING)) > 0;
    }

    @Transactional
    public void updateStatus(Long runId, ImportStatusEnum status) {
        update("status = ?1 where id = ?2", status, runId);
    }

    @Transactional
    public void updateProgressAndStatus(Long runId, Long progress, ImportStatusEnum status, ConnectorRunStep currentStep) {
        update("progress = ?1, status = ?2, currentStep = ?3 where id = ?4", progress, status, currentStep, runId);
    }

    @Transactional
    public void updateStreamingProgress(Long runId,
                                        Long progress,
                                        ImportStatusEnum status,
                                        ConnectorRunStep currentStep,
                                        Long expectedElements,
                                        Long currentElementNr,
                                        Long currentRowNr,
                                        Long progressExtracting) {
        update("""
                        progress = ?1,
                        status = ?2,
                        currentStep = ?3,
                        expectedElements = ?4,
                        currentElementNr = ?5,
                        currentRowNr = ?6,
                        progressExtracting = ?7
                        where id = ?8
                        """,
                progress,
                status,
                currentStep,
                expectedElements,
                currentElementNr,
                currentRowNr,
                progressExtracting,
                runId);
    }

    @Transactional
    public void updateRunStatistics(Long id,
                                    ImportStatusEnum status,
                                    Long progress,
                                    ConnectorRunStep currentStep,
                                    Long expectedElements,
                                    Long progressExtracting,
                                    Long currentTransformingStep,
                                    Long currentElementNr,
                                    Long currentRowNr,
                                    Long newEntities,
                                    Long updatedEntities,
                                    Long deletedEntities,
                                    Long failedEntities,
                                    Long unchangedEntities,
                                    Long receivedEntities,
                                    Long processedEntities,
                                    Long newDataEntries,
                                    Long failedDataEntries,
                                    String errorMessage) {
        update("""
                        status = ?1,
                        progress = ?2,
                        currentStep = ?3,
                        expectedElements = ?4,
                        progressExtracting = ?5,
                        currentTransformingStep = ?6,
                        currentElementNr = ?7,
                        currentRowNr = ?8,
                        newEntities = ?9,
                        updatedEntities = ?10,
                        deletedEntities = ?11,
                        failedEntities = ?12,
                        unchangedEntities = ?13,
                        receivedEntities = ?14,
                        processedEntities = ?15,
                        newDataEntries = ?16,
                        failedDataEntries = ?17,
                        errorMessage = ?18
                        where id = ?19
                        """,
                status,
                progress,
                currentStep,
                expectedElements,
                progressExtracting,
                currentTransformingStep,
                currentElementNr,
                currentRowNr,
                newEntities,
                updatedEntities,
                deletedEntities,
                failedEntities,
                unchangedEntities,
                receivedEntities,
                processedEntities,
                newDataEntries,
                failedDataEntries,
                errorMessage,
                id);
    }
}
