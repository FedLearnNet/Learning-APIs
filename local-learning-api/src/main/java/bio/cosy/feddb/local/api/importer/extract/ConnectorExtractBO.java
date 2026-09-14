package bio.cosy.feddb.local.api.importer.extract;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.local.api.importer.connector.ConnectorBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.connector.input.AppBasedUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesBO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDTO;
import bio.cosy.feddb.local.api.importer.files.read.TabularFileReaderBO;
import bio.cosy.feddb.local.api.importer.files.read.TableReadSpec;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionOutputAwaiter;
import bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionResult;
import bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionRunBO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepBO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepMessageSender;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO.APP_OUTPUT_TIMEOUT;

@ApplicationScoped
public class ConnectorExtractBO {

    @Inject
    ConnectorFilesBO filesBO;

    @Inject
    TabularFileReaderBO fileHandlerBO;

    @Inject
    ConnectorRunExecutionRunBO executionRunBO;

    @Inject
    ConnectorRunStepBO stepBO;

    @Inject
    ConnectorRunStepMessageSender messageSender;

    @Inject
    AppExtractorMapper appExtractorMapper;

    @Inject
    ConnectorRunExecutionOutputAwaiter outputAwaiter;

    @Inject
    FLNetClientConfig config;

    @Inject
    Instance<ConnectorExtractBO> self;

    @Inject
    Instance<ConnectorBO> connectorBO;

    public TableData loadConnectorData(ConnectorDTO connector, ConnectorRunDTO run) {
        if (connector == null) {
            return null;
        }
        return load(new ExtractRequest(
                connector.getInputConfig(),
                connector.getCohortId(),
                connector.getMergeConfig(),
                connector.getPivotConfig(),
                null,
                null,
                false,
                run == null ? null : run.getId(),
                connector.getId(),
                run == null ? null : run.getKeycloakId()
        ));
    }

    public TableData loadConnectorData(
            ConnectorInputConfigDTO config,
            Long cohortId,
            SheetMergeResultDTO mergeConfig,
            PivotConfigDTO pivotConfig
    ) {
        return load(new ExtractRequest(config, cohortId, mergeConfig, pivotConfig));
    }

    public TableData loadConnectorData(
            ConnectorInputConfigDTO config,
            Long cohortId,
            SheetMergeResultDTO mergeConfig,
            PivotConfigDTO pivotConfig,
            Map<String, UploadInfoDTO> uploadInfo
    ) {
        return load(new ExtractRequest(config, cohortId, mergeConfig, pivotConfig, uploadInfo));
    }

    public TableData loadPreviewData(
            ConnectorInputConfigDTO config,
            Long cohortId,
            int maxRows,
            SheetMergeResultDTO mergeConfig,
            PivotConfigDTO pivotConfig
    ) {
        return load(new ExtractRequest(config, cohortId, mergeConfig, pivotConfig, null, maxRows, true));
    }

    public TableData loadPreviewData(
            ConnectorInputConfigDTO config,
            Long cohortId,
            int maxRows,
            SheetMergeResultDTO mergeConfig,
            PivotConfigDTO pivotConfig,
            Map<String, UploadInfoDTO> uploadInfo
    ) {
        return load(new ExtractRequest(
                config, cohortId, mergeConfig, pivotConfig, uploadInfo, maxRows, true));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @TransactionConfiguration(timeout = 3600)
    public ResolvedFileInput loadFileTransactional(FileUploadSettingsDTO fileSettings, Long cohortId) {
        ConnectorFilesDTO fileDto = resolveInputFile(fileSettings, cohortId)
                .orElseThrow(() -> new NotFoundException("No file found for the given file ID or cohort"));
        FileParsingSettingsDTO parsingSettings = resolveParsingSettings(fileSettings, fileDto);
        return new ResolvedFileInput(filesBO.loadFile(fileDto.getId()), parsingSettings);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Map<String, TableSample> loadStoredPreviewTransactional(
            FileUploadSettingsDTO fileSettings,
            Long cohortId
    ) {
        ConnectorFilesDTO fileDto = resolveInputFile(fileSettings, cohortId)
                .orElseThrow(() -> new NotFoundException("No file found for the given file ID or cohort"));
        return filesBO.getPreviewData(fileDto.getId());
    }

    private TableData load(ExtractRequest request) {
        if (request == null || request.getConfig() == null) {
            return null;
        }

        if (request.getConfig() instanceof FileUploadSettingsDTO fileSettings) {
            return loadFromFile(fileSettings, request);
        }
        if (request.getConfig() instanceof AppBasedUploadSettingsDTO appSettings) {
            // A preview never starts the app. Every run stores its outputs as files of the cohort,
            // so previewing reads those, and paging back and forth through the wizard costs
            // nothing. Starting a container here would block the request for as long as the app
            // takes - the preview endpoint is transactional, so the reaper aborted the transaction
            // underneath the waiting thread - and it would reproduce output that already exists.
            // Running the extractor on demand is what the wizard's Start button is for; an import
            // run always runs the app, because that is the point of a run.
            if (request.isPreview()) {
                TableData stored = loadStoredAppOutput(appSettings, request);
                if (stored == null) {
                    throw new NotFoundException(
                            "This extractor has not produced any output yet. Run the extractor "
                                    + "before previewing it.");
                }
                return limit(stored, request.getLimit());
            }
            return loadFromApp(appSettings, request.getCohortId(), request.getConnectorId(),
                    request.getConnectorRunId(), request.getKeycloakId());
        }

        Log.warnf("Unsupported connector input config type: %s", request.getConfig().getClass().getName());
        return null;
    }

    /**
     * Builds the preview from the outputs the last run stored, treating them the way a multi-sheet
     * upload is treated.
     *
     * <p>An extractor that declares several outputs - the US-130 extractor emits the clinical
     * records next to a code lookup table - stores one connector file per output. Presenting them
     * as one table map keyed by output name means the existing sheet handling picks the table,
     * merges them when the connector says to, and shows them as tabs, with no separate notion of
     * "which output" to configure.</p>
     *
     * @return null when nothing is stored yet or none of the stored files can still be read
     */
    private TableData loadStoredAppOutput(AppBasedUploadSettingsDTO appSettings, ExtractRequest request) {
        Map<String, Object> outputParams = appSettings.getOutputParams();
        if (outputParams == null || outputParams.isEmpty()) {
            return null;
        }

        Map<String, TableSample> tables = new LinkedHashMap<>();
        for (Map.Entry<String, Object> output : outputParams.entrySet()) {
            Long fileId = toFileId(output.getValue());
            if (fileId == null) {
                continue;
            }
            FileUploadSettingsDTO storedFile = new FileUploadSettingsDTO();
            storedFile.setFileId(fileId);
            try {
                Map<String, TableSample> stored = self.get()
                        .loadStoredPreviewTransactional(storedFile, request.getCohortId());
                stored.forEach((sheet, sample) ->
                        tables.put(tableName(output.getKey(), sheet, stored.size()), sample));
            } catch (Exception e) {
                Log.warnf("Stored app output '%s' (file %d) is no longer readable: %s",
                        output.getKey(), fileId, e.getMessage());
            }
        }

        if (tables.isEmpty()) {
            return null;
        }

        FileParsingSettingsDTO parsingSettings = new FileParsingSettingsDTO();
        parsingSettings.setFileType(FileParsingType.CSV);
        if (request.getUploadInfo() == null || request.getUploadInfo().isEmpty()) {
            return fileHandlerBO.getFirstPreviewTableData(
                    tables, parsingSettings, request.getLimit(),
                    request.getMergeConfig(), request.getPivotConfig());
        }
        return fileHandlerBO.getFirstPreviewTableData(
                tables, parsingSettings, request.getLimit(),
                request.getMergeConfig(), request.getPivotConfig(), request.getUploadInfo());
    }

    /**
     * Names a stored table after the app output it came from, only qualifying it with the sheet
     * when that one output held more than one table.
     */
    private static String tableName(String outputKey, String sheet, int sheetsInFile) {
        return sheetsInFile <= 1 ? outputKey : outputKey + "/" + sheet;
    }

    private static Long toFileId(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Optional<ConnectorFilesDTO> resolveInputFile(FileUploadSettingsDTO fileSettings, Long cohortId) {
        if (fileSettings == null) {
            return Optional.empty();
        }

        Long fileId = fileSettings.getFileId();
        ConnectorFilesDTO fileDto = Optional.ofNullable(fileId)
                .map(id -> filesBO.getById(id))
                .orElseGet(() -> {
                    List<ConnectorFilesDTO> files = filesBO.getFiles(cohortId);
                    if (files == null || files.isEmpty()) {
                        return null;
                    }
                    return files.stream()
                            .filter(f -> !Boolean.TRUE.equals(f.getIsSupportFile()))
                            .findFirst()
                            .orElse(files.getFirst());
                });
        return Optional.ofNullable(fileDto);
    }

    private TableData loadFromFile(FileUploadSettingsDTO fileSettings, ExtractRequest request) {
        if (request.isPreview()) {
            Map<String, TableSample> sourceTables = self.get().loadStoredPreviewTransactional(
                    fileSettings, request.getCohortId());
            if (sourceTables.isEmpty()) {
                throw new NotFoundException(
                        "Stored preview data is unavailable; upload or re-analyze the file first"
                );
            }
            if (request.getUploadInfo() == null || request.getUploadInfo().isEmpty()) {
                return fileHandlerBO.getFirstPreviewTableData(
                        sourceTables,
                        fileSettings.toFileParsingSettings(),
                        request.getLimit(),
                        request.getMergeConfig(),
                        request.getPivotConfig()
                );
            }
            return fileHandlerBO.getFirstPreviewTableData(
                    sourceTables,
                    fileSettings.toFileParsingSettings(),
                    request.getLimit(),
                    request.getMergeConfig(),
                    request.getPivotConfig(),
                    request.getUploadInfo()
            );
        }

        ResolvedFileInput input = self.get().loadFileTransactional(fileSettings, request.getCohortId());
        return fileHandlerBO.getFirstTableData(
                input.file(),
                TableReadSpec.of(input.parsingSettings(), request.getLimit(), false),
                request.getMergeConfig(),
                request.getPivotConfig(),
                request.getUploadInfo()
        );
    }


    public TableData loadFromApp(AppBasedUploadSettingsDTO appSettings,
                                 Long cohortId,
                                 Long connectorId,
                                 Long connectorRunId,
                                 String keycloakId) {
        if (!hasAppImage(appSettings)) {
            Log.warn("App-based extractor has no appImage configured");
            throw new BadRequestException("App-based extractor has no app image configured");
        }

        ConnectorRunStepDTO step = createStep(appSettings, connectorRunId);
        ConnectorTransformerDTO extractorTransformer = createExtractorTransformer(appSettings, step.getId());
        outputAwaiter.register(step.getId(), cohortId);

        Log.infof("Starting app-based extractor (image: %s, step: %d, run: %s)",
                appSettings.getAppImage(), step.getId(), connectorRunId);

        try {
            ConnectorRunStepDTO result = executionRunBO.startModel(extractorTransformer, step, keycloakId);
            if (!isSuccessfulAppStart(result, appSettings)) {
                outputAwaiter.cancel(step.getId());
                String error = result == null || result.getLastError() == null || result.getLastError().isBlank()
                        ? "App-based extractor '" + appSettings.getAppImage() + "' could not be started"
                        : result.getLastError();
                markStepError(step, error);
                throw new AppExtractorException(error);
            }

            ConnectorRunExecutionResult finishedStep = outputAwaiter.awaitOutput(result.getId())
                    .await().atMost(APP_OUTPUT_TIMEOUT);
            recordStoredOutputs(appSettings, connectorId, finishedStep, keycloakId);
            TableData outputData = resolveAppOutput(finishedStep, appSettings);
            if (outputData == null) {
                String error = "App-based extractor '" + appSettings.getAppImage()
                        + "' finished without tabular output";
                markStepError(step, error);
                throw new AppExtractorException(error);
            }
            return outputData;

        } catch (AppExtractorException e) {
            throw e;
        } catch (Exception e) {
            Log.errorf(e, "Failed to run app-based extractor with image '%s'", appSettings.getAppImage());
            outputAwaiter.cancel(step.getId());
            String error = "Failed to run app-based extractor: " + e.getMessage();
            markStepError(step, error);
            throw new AppExtractorException(error, e);
        }
    }
    public static class AppExtractorException extends RuntimeException {
        public AppExtractorException(String message) {
            super(message);
        }

        public AppExtractorException(String message, Throwable cause) {
            super(message, cause);
        }
    }


    private void recordStoredOutputs(AppBasedUploadSettingsDTO appSettings,
                                     Long connectorId,
                                     ConnectorRunExecutionResult finishedStep,
                                     String keycloakId) {
        if (finishedStep == null || finishedStep.storedOutputs() == null
                || finishedStep.storedOutputs().isEmpty()) {
            return;
        }
        appSettings.setOutputParams(new LinkedHashMap<>(finishedStep.storedOutputs()));
        if (connectorId == null) {
            return;
        }
        try {
            connectorBO.get().updateInputConfigTransactional(connectorId, appSettings);
            Log.infof("Connector %d now points at app outputs %s",
                    connectorId, appSettings.getOutputParams().keySet());
        } catch (Exception e) {
            Log.errorf("Could not record app outputs on connector %d: %s", connectorId, e.getMessage());
        }
    }

    private static FileParsingType resolveFileType(String contentType) {
        if ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType)) {
            return FileParsingType.EXCEL;
        }
        if ("application/json".equals(contentType)) {
            return FileParsingType.JSON;
        }
        if ("application/zip".equals(contentType)) {
            return FileParsingType.MULTIPLE_CSV_ZIP;
        }
        return FileParsingType.CSV;
    }

    private static FileParsingSettingsDTO resolveParsingSettings(
            FileUploadSettingsDTO fileSettings,
            ConnectorFilesDTO fileDto
    ) {
        FileParsingSettingsDTO parsingSettings = Optional.ofNullable(fileDto.getUploadSettings())
                .orElseGet(fileSettings::toFileParsingSettings);
        if (parsingSettings.getFileType() == null) {
            parsingSettings.setFileType(resolveFileType(fileDto.getContentType()));
        }
        return parsingSettings;
    }

    public record ResolvedFileInput(File file, FileParsingSettingsDTO parsingSettings) {
    }

    public Multi<ConnectorExtractorStreamDTO> startFromApp(AppBasedUploadSettingsDTO appSettings, Long cohortId, String keycloakId) {
        return Multi.createFrom().emitter(emitter -> {
            if (!hasAppImage(appSettings)) {
                Log.warn("App-based extractor has no appImage configured");
                emitter.complete();
                return;
            }

            ConnectorRunStepDTO step = createStep(appSettings);
            ConnectorTransformerDTO extractorTransformer = createExtractorTransformer(appSettings, step.getId());
            outputAwaiter.register(step.getId(), cohortId);

            ConnectorExtractorStreamDTO initialDto = appExtractorMapper.dtoToStreamDTO(step);
            initialDto.setCached(false);
            messageSender.sendMessage(initialDto);

            ConnectorRunStepDTO finalStep = step;
            Infrastructure.getDefaultExecutor().execute(() -> {
                try {
                    ConnectorRunStepDTO result = executionRunBO.startModel(extractorTransformer, finalStep, keycloakId);
                    if (!isSuccessfulAppStart(result, appSettings)) {
                        emitter.complete();
                        return;
                    }

                    ConnectorExtractorStreamDTO updateDto = appExtractorMapper.dtoToStreamDTO(result);
                    updateDto.setCached(false);
                    messageSender.sendMessage(updateDto);

                    outputAwaiter.awaitOutput(result.getId())
                            .onItem().transform(finishedStep -> {
                                TableData outputData = resolveAppOutput(finishedStep, appSettings);
                                if (outputData == null) {
                                    throw new RuntimeException("App transformer finished without tabular output");
                                }
                                TableData previewData = limit(outputData, config.connector().previewRows());
                                if (previewData != outputData) {
                                    outputData.close();
                                }
                                ConnectorFileUploadInfoDTO fileUpload = new ConnectorFileUploadInfoDTO(previewData, fileHandlerBO.getJson(previewData));

                                ConnectorRunStepDTO finishedRun = finishedStep == null ? result : finishedStep.run();
                                ConnectorExtractorStreamDTO finalDto = appExtractorMapper.dtoToStreamDTO(finishedRun);
                                finalDto.setUploadInfo(List.of(fileUpload));
                                finalDto.setCached(false);

                                emitter.emit(finalDto);
                                messageSender.sendMessage(finalDto);
                                return finalDto;
                            })
                            .subscribe().with(
                                    finalDto -> emitter.complete(),
                                    e -> {
                                        Log.errorf(e, "Failed while waiting for uploaded output for image '%s'", appSettings.getAppImage());
                                        markStepError(finalStep, "Failed to process uploaded output: " + e.getMessage());

                                        ConnectorExtractorStreamDTO errorDto = appExtractorMapper.dtoToStreamDTO(finalStep);
                                        errorDto.setCached(false);

                                        emitter.emit(errorDto);
                                        messageSender.sendMessage(errorDto);
                                        emitter.complete();
                                    }
                            );

                } catch (Exception e) {
                    Log.errorf(e, "Failed to start app-based extractor with image '%s'", appSettings.getAppImage());
                    markStepError(finalStep, "Failed to start app-based extractor: " + e.getMessage());

                    ConnectorExtractorStreamDTO errorDto = appExtractorMapper.dtoToStreamDTO(finalStep);
                    errorDto.setCached(false);

                    emitter.emit(errorDto);
                    messageSender.sendMessage(errorDto);
                    emitter.complete();
                }
            });
        });
    }

    private ConnectorRunStepDTO createStep(AppBasedUploadSettingsDTO appSettings) {
        return createStep(appSettings, null);
    }

    private ConnectorRunStepDTO createStep(AppBasedUploadSettingsDTO appSettings, Long connectorRunId) {
        ConnectorRunStepDTO step = new ConnectorRunStepDTO();
        step.setTransformationId(null);
        step.setConnectorRunId(connectorRunId);
        step.setStatus(RunStatusTypes.PENDING);
        step.setProgress(0f);
        step.setHyperParams(appSettings.getHyperParams());
        return stepBO.createInNewTransaction(step);
    }

    private ConnectorTransformerDTO createExtractorTransformer(AppBasedUploadSettingsDTO appSettings, Long stepId) {
        ConnectorTransformerDTO extractorTransformer = new ConnectorTransformerDTO();
        extractorTransformer.setId(stepId);
        extractorTransformer.setAppImage(appSettings.getAppImage());
        extractorTransformer.setAppVersionId(
                appSettings.getAppVersionId() != null ? Long.valueOf(appSettings.getAppVersionId()) : null
        );
        extractorTransformer.setHyperparams(appSettings.getHyperParams());
        return extractorTransformer;
    }

    private boolean isSuccessfulAppStart(ConnectorRunStepDTO result, AppBasedUploadSettingsDTO appSettings) {
        if (result == null) {
            Log.warnf("App-based extractor '%s' returned no execution result", appSettings.getAppImage());
            return false;
        }

        if (result.getLastError() != null && !result.getLastError().isBlank()) {
            Log.errorf("App-based extractor failed: %s", result.getLastError());
            return false;
        }

        return true;
    }

    private TableData resolveAppOutput(ConnectorRunExecutionResult finishedStep, AppBasedUploadSettingsDTO appSettings) {
        if (finishedStep != null && finishedStep.outputData() != null) {
            return finishedStep.outputData();
        }

        Log.warn("App-based extractor finished without uploaded tabular output");
        return null;
    }

    private boolean hasAppImage(AppBasedUploadSettingsDTO appSettings) {
        return appSettings != null
                && appSettings.getAppImage() != null
                && !appSettings.getAppImage().isBlank();
    }

    private void markStepError(ConnectorRunStepDTO step, String message) {
        if (step == null) {
            return;
        }

        stepBO.updateAndNotify(step.getId(), RunStatusTypes.ERROR, message);
        step.setStatus(RunStatusTypes.ERROR);
        step.setLastError(message);
        step.setProgress(100f);
    }

    private TableData limit(TableData data, Integer maxRows) {
        if (data == null || maxRows == null) {
            return data;
        }
        return data.limit(maxRows);
    }


}
