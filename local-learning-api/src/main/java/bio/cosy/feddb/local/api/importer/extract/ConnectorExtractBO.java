package bio.cosy.feddb.local.api.importer.extract;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.failure.FailureCategory;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.local.api.importer.connector.ConnectorBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.input.AppBasedUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesBO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDTO;
import bio.cosy.feddb.local.api.importer.files.read.TableReadSpec;
import bio.cosy.feddb.local.api.importer.files.read.TabularFileReaderBO;
import bio.cosy.feddb.local.api.importer.files.table.SheetMergePlan;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.run.ConnectorAppOutputDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorAppRunBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.execution.run.ExecutionRunBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.io.File;
import java.util.*;

import static bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO.APP_OUTPUT_TIMEOUT;

@ApplicationScoped
public class ConnectorExtractBO {

    @Inject
    ConnectorFilesBO filesBO;

    @Inject
    TabularFileReaderBO fileHandlerBO;

    @Inject
    ConnectorAppRunBO appRunBO;

    @Inject
    ExecutionRunBO runBO;

    @Inject
    AppExtractorMapper appExtractorMapper;

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
                connector.getUploadInfo(),
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
                    request.getConnectorRunId(), request.getKeycloakId(), request);
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
        Set<String> selected = selectedOutputKeys(appSettings);

        Map<String, TableSample> tables = new LinkedHashMap<>();
        for (Map.Entry<String, Object> output : outputParams.entrySet()) {
            if (!selected.isEmpty() && !selected.contains(output.getKey())) {
                continue;
            }
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

    private static Set<String> selectedOutputKeys(AppBasedUploadSettingsDTO appSettings) {
        Map<String, Object> outputParams = appSettings.getOutputParams();
        List<String> selected = appSettings.getSelectedOutputs();
        if (outputParams == null || selected == null) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        outputParams.keySet().stream().filter(selected::contains).forEach(keys::add);
        return keys;
    }


    private TableData loadSelectedStoredOutputs(AppBasedUploadSettingsDTO appSettings, Long cohortId,
                                                ExtractRequest request) {
        Map<String, TableData> tables = new LinkedHashMap<>();
        for (String output : selectedOutputKeys(appSettings)) {
            Long fileId = toFileId(appSettings.getOutputParams().get(output));
            if (fileId == null) {
                continue;
            }
            FileUploadSettingsDTO storedFile = new FileUploadSettingsDTO();
            storedFile.setFileId(fileId);
            ResolvedFileInput input = self.get().loadFileTransactional(storedFile, cohortId);
            Map<String, TableData> read = fileHandlerBO
                    .getByFile(input.file(), TableReadSpec.whole(input.parsingSettings()), null)
                    .tables();
            int sheets = read.size();
            read.forEach((sheet, table) -> tables.put(tableName(output, sheet, sheets), table));
        }
        if (tables.isEmpty()) {
            return null;
        }
        return fileHandlerBO.getTableData(
                tables, request.getMergeConfig(), request.getPivotConfig(), request.getUploadInfo());
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
        if (request.isPreview() && SheetMergePlan.requested(request.getMergeConfig())) {
            ResolvedFileInput input = self.get().loadFileTransactional(fileSettings, request.getCohortId());
            return fileHandlerBO.getMergedPreviewTableData(
                    input.file(),
                    input.parsingSettings(),
                    request.getLimit(),
                    request.getMergeConfig(),
                    request.getPivotConfig(),
                    request.getUploadInfo()
            );
        }
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
                                 String keycloakId,
                                 ExtractRequest request) {
        if (!hasAppImage(appSettings)) {
            Log.warn("App-based extractor has no appImage configured");
            throw new BadRequestException("App-based extractor has no app image configured");
        }
        Log.infof("Starting app-based extractor (image: %s, import: %s)", appSettings.getAppImage(), connectorRunId);
        try {
            ExecutionRunDTO run = appRunBO.start(appSettings.getExtractor(), appSettings.getHyperParams(), connectorRunId,
                    cohortId, keycloakId, false);
            // The import reads the table next, so it waits here; the timeout stops the run.
            ConnectorAppOutputDTO output = appRunBO.output(run.getId(), APP_OUTPUT_TIMEOUT).await().indefinitely();
            recordStoredOutputs(appSettings, connectorId, output, keycloakId);
            TableData outputData = loadSelectedStoredOutputs(appSettings, cohortId, request);
            if (outputData == null) {
                return output.getOutputData();
            }
            output.getOutputData().close();
            return outputData;
        } catch (RuntimeException e) {
            Log.errorf(e, "Failed to run app-based extractor with image '%s'", appSettings.getAppImage());
            throw new AppExtractorException("Failed to run app-based extractor: " + e.getMessage(), e);
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
                                     ConnectorAppOutputDTO output,
                                     String keycloakId) {
        if (output.getStoredOutputs() == null || output.getStoredOutputs().isEmpty()) {
            return;
        }
        appSettings.setOutputParams(new LinkedHashMap<>(output.getStoredOutputs()));
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
        if (!hasAppImage(appSettings)) {
            Log.warn("App-based extractor has no appImage configured");
            return Multi.createFrom().empty();
        }
        ExecutionRunDTO run = appRunBO.start(appSettings.getExtractor(), appSettings.getHyperParams(), null, cohortId, keycloakId, false);
        return Multi.createBy().concatenating().streams(
                runBO.follow(run.getId()).map(this::streamOf),
                Multi.createFrom().uni(previewOf(run, appSettings)));
    }

    // Subscribed once the run's stream completed, so the run has ended.
    private Uni<ConnectorExtractorStreamDTO> previewOf(ExecutionRunDTO run, AppBasedUploadSettingsDTO appSettings) {
        return appRunBO.output(run.getId(), APP_OUTPUT_TIMEOUT)
                .map(output -> {
                    TableData outputData = output.getOutputData();
                    TableData previewData = limit(outputData, config.connector().previewRows());
                    if (previewData != outputData) {
                        outputData.close();
                    }
                    ConnectorExtractorStreamDTO done = streamOf(output.getRun());
                    done.setUploadInfo(List.of(new ConnectorFileUploadInfoDTO(previewData, fileHandlerBO.getJson(previewData))));
                    return done;
                })
                .onFailure().recoverWithItem(e -> {
                    Log.errorf(e, "App-based extractor with image '%s' failed", appSettings.getAppImage());
                    ConnectorExtractorStreamDTO failed = streamOf(run);
                    failed.setStatus(RunStatusTypes.ERROR);
                    failed.setFailure(new ExecutionFailure(FailureCategory.TOOL, e.getMessage()));
                    return failed;
                });
    }

    private ConnectorExtractorStreamDTO streamOf(ExecutionRunDTO run) {
        ConnectorExtractorStreamDTO stream = appExtractorMapper.runToStreamDTO(run);
        stream.setCached(false);
        return stream;
    }

    private boolean hasAppImage(AppBasedUploadSettingsDTO appSettings) {
        return appSettings != null
                && appSettings.getAppImage() != null
                && !appSettings.getAppImage().isBlank();
    }

    private TableData limit(TableData data, Integer maxRows) {
        if (data == null || maxRows == null) {
            return data;
        }
        return data.limit(maxRows);
    }


}
