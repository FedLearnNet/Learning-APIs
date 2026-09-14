package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.local.api.cohort.patient.PatientBO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.crud.AuditContext;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorTriggerBO;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.extract.ConnectorExtractBO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesBO;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.files.table.TableDataGroup;
import bio.cosy.feddb.local.api.importer.files.table.TableDataGroupCursor;
import bio.cosy.feddb.local.api.importer.files.table.TableDataGroupingBO;
import bio.cosy.feddb.local.api.importer.files.table.TableDataGroups;
import bio.cosy.feddb.local.api.importer.load.ConnectorLoadBO;
import bio.cosy.feddb.local.api.importer.mapping.MappingBO;
import bio.cosy.feddb.local.api.importer.mapping.MappingRowResultDTO;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunMessageLevels;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunRunMessagesBO;
import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogBO;
import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogType;
import bio.cosy.feddb.local.api.importer.transformer.AppTransformerSessionBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.context.api.ManagedExecutorConfig;
import io.smallrye.context.api.NamedInstance;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class ConnectorRunETLBO {

    /** How often the load phase reports progress to the log. */
    private static final long DRAIN_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);

    @Inject
    Instance<ConnectorRunETLBO> self;

    @Inject
    ConnectorRunAO runAO;

    @Inject
    ConnectorTransformerBO transformerBO;

    @Inject
    AppTransformerSessionBO appTransformerSessionBO;

    @Inject
    ConnectorRunPatientLogBO errorLogBO;

    @Inject
    MappingBO mappingBO;

    @Inject
    ConnectorExtractBO extractBO;

    @Inject
    TableDataGroupingBO tableDataGroupingBO;

    @Inject
    ConnectorRunResultSender resultSender;

    @Inject
    ConnectorLoadBO connectorLoadBO;

    @Inject
    ConnectorFilesBO connectorFilesBO;

    @Inject
    ConnectorRunRunMessagesBO runMessagesBO;

    @Inject
    PatientBO patientBO;

    @Inject
    ConnectorTriggerBO triggerBO;

    @Inject
    FLNetClientConfig config;

    @Inject
    @NamedInstance("connector-etl-executor")
    @ManagedExecutorConfig(maxAsync = 1, maxQueued = 16, propagated = {}, cleared = ThreadContext.ALL_REMAINING)
    ManagedExecutor etlExecutor;

    public void processRunAsync(ConnectorRunDTO run, ConnectorDTO connectorDTO) {
        assert Objects.equals(run.getCohortId(), connectorDTO.getCohortId());
        etlExecutor.execute(() -> {
            try {
                self.get().processRun(run, connectorDTO);
            } catch (Throwable throwable) {
                Log.errorf(throwable, "Async ETL execution failed for run %d", run.getId());
            }
        });
    }

    /**
     * Runs the memory-bounded, disk-backed streaming ETL pipeline for a single connector run:
     * - EXTRACT — load connector data and merge sheets through disk-backed TableData.
     * - GROUP INDEX — build a disk-backed row index by the raw patient-id source column.
     * - DRAIN — stream one indexed patient group at a time through
     * transform, map and load. Peak memory is a single patient
     */
    @ActivateRequestContext
    public void processRun(ConnectorRunDTO run, ConnectorDTO connectorDTO) {
        assert Objects.equals(run.getCohortId(), connectorDTO.getCohortId());

        runMessagesBO.createTransactional("Starting import", ConnectorRunMessageLevels.INFO, run.getId());
        Log.infof("ETL [Starting] process for run %d (deleteExistingPatients:  %s)", run.getId(),
                run.getDeleteExistingPatients());

        boolean dryRun = Boolean.TRUE.equals(run.getDryRun());
        TableData extractedData = null;
        TableDataGroups groupedData = null;
        try {
            AuditContext.setThreadLocalContext(
                    run.getKeycloakId(),
                    connectorDTO.getId(),
                    run.getId());
            run.initializeForImport();

            // The patient grouping happens BEFORE mapping, so resolve the raw source column that
            // carries the external patient id from the connector's schema mapping.
            String sourceColumn = mappingBO.resolveExternalIdSourceColumn(connectorDTO.getSchemaMapping());
            if (sourceColumn == null) {
                failRun(run, "No source column is mapped to the external patient id ("
                        + config.connector().externalIdColumn() + "); cannot group rows by patient.",
                        ConnectorRunPatientLogType.MAPPING);
                return;
            }

            boolean hasAppBasedTransformers = openAppTransformerSession(connectorDTO, run);

            runAO.updateStatus(run.getId(), ImportStatusEnum.RUNNING);
            sendProgress(run, 5L, ImportStatusEnum.RUNNING, ConnectorRunStep.EXTRACTING);

            // --- PHASE 1: EXTRACT ---
            runMessagesBO.createTransactional("Starting data extraction", ConnectorRunMessageLevels.INFO, run.getId());
            Log.infof("ETL [Extract] loading input data for run %d", run.getId());
            extractedData = extractBO.loadConnectorData(connectorDTO, run);
            if (extractedData == null) {
                failRun(run, "Could not load input data. Check that the uploaded file matches the connector file type.",
                        ConnectorRunPatientLogType.EXTRACTING);
                return;
            }
            long extractedRows = extractedData.longSize();
            run.setProgressExtracting(extractedRows);
            persistStreamingProgress(run, 5L, ImportStatusEnum.RUNNING, ConnectorRunStep.EXTRACTING);

            List<String> extractedColumns = extractedData.getColumns();
            if (extractedColumns != null && !extractedColumns.isEmpty()
                    && !extractedColumns.contains(sourceColumn)) {
                failRun(run, "The extracted data has no column '" + sourceColumn + "', which this connector"
                                + " maps to the external patient id. Columns found: "
                                + String.join(", ", extractedColumns),
                        ConnectorRunPatientLogType.MAPPING);
                return;
            }

            if (extractedRows == 0L) {
                runMessagesBO.createTransactional("No data to import", ConnectorRunMessageLevels.INFO, run.getId());
                Log.infof("ETL [Done] run %d had no input rows", run.getId());
                persistRunStatistics(run, 100L, ImportStatusEnum.FINISHED, ConnectorRunStep.FINISHED);
                return;
            }

            // --- PHASE 2: DISK-BACKED GROUP INDEX BY PATIENT KEY ---
            runMessagesBO.createTransactional(String.format("Grouping %d rows by patient", extractedRows),
                    ConnectorRunMessageLevels.INFO, run.getId());
            Log.infof("ETL [Group] disk-backed grouping index for %d rows in run %d", extractedRows, run.getId());
            groupedData = tableDataGroupingBO.groupByColumn(extractedData, sourceColumn,
                    Math.max(1, config.connector().etlSortChunkSize()));
            long totalPatients = groupedData.groupCount();
            run.setExpectedElements(totalPatients);
            runMessagesBO.createTransactional(String.format("Found %d patients", totalPatients),
                    ConnectorRunMessageLevels.INFO, run.getId());
            Log.infof("ETL [Group] found %d patients in %d rows for run %d",
                    totalPatients, extractedRows, run.getId());
            persistStreamingProgress(run, 10L, ImportStatusEnum.RUNNING, ConnectorRunStep.TRANSFORMING);

            // deleteExistingPatients runs once, before the drain loop (not in dry run mode).
            if (!dryRun && Boolean.TRUE.equals(run.getDeleteExistingPatients())) {
                runMessagesBO.createTransactional("Deleting all existing patients",
                        ConnectorRunMessageLevels.INFO, run.getId());
                Log.infof("Deleting all existing patients before load phase for run %d", run.getId());
                run.setDeletedEntities(patientBO.deleteAllPatients(run.getCohortId()));
            }

            if (dryRun) {
                runMessagesBO.createTransactional("Dry run mode: load phase will be skipped",
                        ConnectorRunMessageLevels.INFO, run.getId());
            } else {
                runMessagesBO.createTransactional(String.format("Starting load (%d patients)", totalPatients),
                        ConnectorRunMessageLevels.INFO, run.getId());
            }

            // --- PHASE 3: DRAIN (one patient group at a time) ---
            // Build the mapping lookup context (incl. its validation-node query) once, not per patient.
            MappingBO.MappingContext mappingContext = mappingBO.buildContext(connectorDTO.getSchemaMapping());
            drainPatientGroups(
                    groupedData,
                    connectorDTO,
                    run,
                    mappingContext,
                    dryRun,
                    totalPatients,
                    hasAppBasedTransformers
            );
            long elementNr = orZero(run.getCurrentElementNr());
            long rowNr = orZero(run.getCurrentRowNr());

            if (dryRun) {
                Log.infof("Dry run completed for run %d: %d patients (%d rows) would be imported",
                        run.getId(), elementNr, rowNr);
            }

            Log.infof("ETL [Done] process for run %d (%d patients, %d rows)", run.getId(), elementNr, rowNr);
            persistRunStatistics(run, 100L, ImportStatusEnum.FINISHED, ConnectorRunStep.FINISHED);

            cleanupInputFileOnSuccess(connectorDTO, run);
            triggerBO.onConnectorSuccess(connectorDTO.getId());
        } catch (Exception e) {
            Log.errorf(e, "Unexpected error in run %d", run.getId());
            errorLogBO.createLog("Unexpected error: " + e.getMessage(), run.getId(),
                    ConnectorRunPatientLogType.EXTRACTING);
            run.setErrorMessage(e.getMessage());
            persistRunStatistics(run, 100L, ImportStatusEnum.ERROR, run.getCurrentStep());
        } finally {
            AuditContext.clearThreadLocalContext();
            appTransformerSessionBO.close(run.getId());
            if (groupedData != null) {
                groupedData.close();
            }
            if (extractedData != null) {
                extractedData.close();
            }
            runMessagesBO.createTransactional(getFinalMessage(run), ConnectorRunMessageLevels.INFO, run.getId());
        }
    }

    private void drainPatientGroups(
            TableDataGroups groupedData,
            ConnectorDTO connectorDTO,
            ConnectorRunDTO run,
            MappingBO.MappingContext mappingContext,
            boolean dryRun,
            long totalPatients,
            boolean hasAppBasedTransformers) throws Exception {
        int publishBatchSize = Math.max(1, config.connector().importProgressPublishBatchSize());
        int parallelism = hasAppBasedTransformers ? 1 : Math.max(1, config.connector().etlParallelism());

        // The load phase is the longest part of a large run and used to log nothing between its
        // first and last line, which is indistinguishable from a hang. Progress is reported on a
        // timer rather than per patient so the interval stays the same whatever the row count is.
        long startedNanos = System.nanoTime();
        long nextProgressLogNanos = startedNanos + DRAIN_LOG_INTERVAL_NANOS;

        try (TableDataGroupCursor cursor = groupedData.cursor()) {
            List<TableDataGroup> patientBatch;
            while (!(patientBatch = readPatientBatch(cursor, parallelism)).isEmpty()) {
                for (PatientGroupResultDTO result : processPatientBatch(
                        patientBatch,
                        connectorDTO,
                        run,
                        mappingContext,
                        dryRun,
                        parallelism
                )) {
                    run.addPatientGroupResult(result);
                    long processedPatients = orZero(run.getCurrentElementNr());
                    if (processedPatients % publishBatchSize == 0L) {
                        persistRunStatistics(
                                run,
                                drainProgress(processedPatients, totalPatients),
                                ImportStatusEnum.RUNNING,
                                ConnectorRunStep.LOADING
                        );
                    }
                    long now = System.nanoTime();
                    if (now >= nextProgressLogNanos) {
                        nextProgressLogNanos = now + DRAIN_LOG_INTERVAL_NANOS;
                        Log.infof("ETL [Load] run %d: %d/%d patients (%d rows) after %d s",
                                run.getId(),
                                processedPatients,
                                totalPatients,
                                orZero(run.getCurrentRowNr()),
                                TimeUnit.NANOSECONDS.toSeconds(now - startedNanos));
                    }
                }
            }
        }
    }

    private List<TableDataGroup> readPatientBatch(TableDataGroupCursor cursor, int maxBatchSize) throws Exception {
        List<TableDataGroup> batch = new ArrayList<>(maxBatchSize);
        while (batch.size() < maxBatchSize) {
            TableDataGroup group = cursor.next();
            if (group == null) {
                break;
            }
            batch.add(group);
        }
        return batch;
    }

    /**
     * A batch may contain several patients, but every item remains an independent patient group
     * with its own worker state and load transaction.
     */
    private List<PatientGroupResultDTO> processPatientBatch(
            List<TableDataGroup> patientBatch,
            ConnectorDTO connectorDTO,
            ConnectorRunDTO run,
            MappingBO.MappingContext mappingContext,
            boolean dryRun,
            int parallelism) {
        return Multi.createFrom().iterable(patientBatch)
                .onItem().transformToUni(group -> Uni.createFrom().item(() ->
                                processPatientGroup(connectorDTO, run, mappingContext, group, dryRun))
                        .runSubscriptionOn(Infrastructure.getDefaultExecutor()))
                .merge(parallelism)
                .collect().asList()
                .await().indefinitely();
    }

    private PatientGroupResultDTO processPatientGroup(
            ConnectorDTO connectorDTO,
            ConnectorRunDTO run,
            MappingBO.MappingContext mappingContext,
            TableDataGroup group,
            boolean dryRun) {
        AuditContext.setThreadLocalContext(run.getKeycloakId(), connectorDTO.getId(), run.getId());
        ConnectorRunDTO workerRun = run.createPatientWorker();
        long rowCount = group.rows().size();

        try {
            // TRANSFORM (per patient group; correct for all row/cell built-in functions).
            workerRun.setCurrentStep(ConnectorRunStep.TRANSFORMING);
            List<Map<String, Object>> transformed =
                    transformerBO.applyTransformations(connectorDTO, workerRun, new ArrayList<>(group.rows()));
            if (transformed == null) {
                throw new IllegalStateException("Transformation returned null");
            }
            if (transformed.isEmpty()) {
                workerRun.markPatientUnchanged();
                return PatientGroupResultDTO.from(workerRun, rowCount);
            }

            // MAP (pure row-by-row using the prebuilt lookup context).
            workerRun.setCurrentStep(ConnectorRunStep.MAPPING);
            List<MappingRowResultDTO> mapped =
                    mappingBO.applyMapping(run.getId(), transformed, mappingContext);
            if (mapped == null || mapped.isEmpty()) {
                throw new IllegalStateException("Mapping returned no rows");
            }

            // LOAD (one patient). Each parallel worker owns zero-based counters; ETL merges
            // those per-patient deltas into the master run after the worker completes.
            workerRun.setCurrentStep(ConnectorRunStep.LOADING);
            if (dryRun) {
                connectorLoadBO.validateSinglePatient(mapped);
                workerRun.markPatientProcessed();
            } else {
                connectorLoadBO.loadPatient(connectorDTO, workerRun, mapped);
            }

            return PatientGroupResultDTO.from(workerRun, rowCount);
        } catch (Exception e) {
            Log.errorf(e, "ETL failed for patient '%s' in run %d", group.key(), run.getId());
            errorLogBO.createLog(
                    "Patient processing failed: " + e.getMessage(),
                    run.getId(),
                    logTypeFor(workerRun.getCurrentStep()),
                    "ERROR",
                    group.key(),
                    null
            );
            workerRun.markPatientFailed();
            return PatientGroupResultDTO.from(workerRun, rowCount);
        } finally {
            AuditContext.clearThreadLocalContext();
        }
    }

    private ConnectorRunPatientLogType logTypeFor(ConnectorRunStep step) {
        if (step == ConnectorRunStep.TRANSFORMING) {
            return ConnectorRunPatientLogType.TRANSFORM;
        }
        if (step == ConnectorRunStep.MAPPING) {
            return ConnectorRunPatientLogType.MAPPING;
        }
        return ConnectorRunPatientLogType.LOADING;
    }

    private boolean openAppTransformerSession(ConnectorDTO connectorDTO, ConnectorRunDTO run) {
        if (AppTransformerSessionBO.appTransformersOf(connectorDTO).isEmpty()) {
            return false;
        }
        runMessagesBO.createTransactional("Starting app-based transformers",
                ConnectorRunMessageLevels.INFO, run.getId());
        appTransformerSessionBO.open(connectorDTO, run);
        return true;
    }

    private long drainProgress(long elementNr, long totalPatients) {
        if (totalPatients <= 0) {
            return 99L;
        }
        long progress = 10L + Math.round(89.0 * elementNr / totalPatients);
        return Math.min(99L, progress);
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    private void cleanupInputFileOnSuccess(ConnectorDTO connectorDTO, ConnectorRunDTO run) {
        if (connectorDTO.getInputConfig() instanceof FileUploadSettingsDTO fileSettings
                && Boolean.TRUE.equals(fileSettings.getDeleteUnneededFileAfterSuccess())) {
            connectorFilesBO.cleanupFileIfNotNeededTransactional(connectorDTO, fileSettings, run.getId());
        }
    }

    private void failRun(ConnectorRunDTO run, String message, ConnectorRunPatientLogType logType) {
        Log.errorf("ETL run %d failed: %s", run.getId(), message);
        errorLogBO.createLog(message, run.getId(), logType);
        run.setErrorMessage(message);
        persistRunStatistics(run, 100L, ImportStatusEnum.ERROR, run.getCurrentStep());
    }

    /**
     * Persists all accumulated statistics from the in-memory {@link ConnectorRunDTO} to the database
     * in a single atomic update, then notifies SSE subscribers.
     */
    private void persistRunStatistics(ConnectorRunDTO run, long progress, ImportStatusEnum status,
            ConnectorRunStep currentStep) {
        run.setProgress(progress);
        run.setStatus(status);
        run.setCurrentStep(currentStep);
        runAO.updateRunStatistics(
                run.getId(),
                status,
                progress,
                currentStep,
                run.getExpectedElements(),
                run.getProgressExtracting(),
                run.getCurrentTransformingStep(),
                run.getCurrentElementNr(),
                run.getCurrentRowNr(),
                run.getNewEntities(),
                run.getUpdatedEntities(),
                run.getDeletedEntities(),
                run.getFailedEntities(),
                run.getUnchangedEntities(),
                run.getReceivedEntities(),
                run.getProcessedEntities(),
                run.getNewDataEntries(),
                run.getFailedDataEntries(),
                run.getErrorMessage());
        resultSender.sendUpdate(run);
    }

    /** Persists the bounded streaming progress (no entity counters) and notifies SSE subscribers. */
    private void persistStreamingProgress(ConnectorRunDTO run, long progress, ImportStatusEnum status,
            ConnectorRunStep currentStep) {
        run.setProgress(progress);
        run.setStatus(status);
        run.setCurrentStep(currentStep);
        runAO.updateStreamingProgress(
                run.getId(),
                progress,
                status,
                currentStep,
                run.getExpectedElements(),
                run.getCurrentElementNr(),
                run.getCurrentRowNr(),
                run.getProgressExtracting());
        resultSender.sendUpdate(run);
    }

    private void sendProgress(ConnectorRunDTO run, long progress, ImportStatusEnum status,
            ConnectorRunStep currentStep) {
        runAO.updateProgressAndStatus(run.getId(), progress, status, currentStep);
        run.setProgress(progress);
        run.setStatus(status);
        run.setCurrentStep(currentStep);
        resultSender.sendUpdate(run);
    }

    private String getFinalMessage(ConnectorRunDTO run) {
        String finalMessage;
        if (run.getStatus() == ImportStatusEnum.FINISHED) {
            if (run.getFailedEntities() > 0) {
                finalMessage = String.format(
                        "Import finished, but encountered errors in case of %d patients. See error logs for details.",
                        run.getFailedEntities());
            } else {
                finalMessage = "Import finished successfully";
            }
        } else {
            finalMessage = "Import finished with errors. See error logs for details.";
        }
        return finalMessage;
    }
}
