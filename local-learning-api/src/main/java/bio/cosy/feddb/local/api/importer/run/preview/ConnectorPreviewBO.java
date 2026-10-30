package bio.cosy.feddb.local.api.importer.run.preview;

import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.ConnectorExtractBO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.read.TabularFileReaderBO;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import bio.cosy.feddb.local.api.importer.functions.FunctionRunnerBO;
import bio.cosy.feddb.local.api.importer.mapping.MappingBO;
import bio.cosy.feddb.local.api.importer.run.preview.cache.ConnectorPreviewTransformationCacheBO;
import bio.cosy.feddb.local.api.importer.run.preview.cache.ConnectorPreviewTransformationCacheStage;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.importer.extract.UploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.table.SheetMergePlan;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Stream;

@ApplicationScoped
public class ConnectorPreviewBO {

    private static final int DEFAULT_PREVIEW_ROWS = ConnectorFileUploadSettingsDTO.DEFAULT_PREVIEW_ROWS;
    private static final int DEFAULT_PREVIEW_PATIENTS = 10;

    @Inject
    ConnectorExtractBO extractBO;

    @Inject
    TabularFileReaderBO fileHandlerBO;

    @Inject
    ConnectorTransformerBO transformerBO;

    @Inject
    FunctionRunnerBO functionRunnerBO;

    @Inject
    MappingBO mappingBO;

    @Inject
    ConnectorPreviewTransformationCacheBO cacheBO;

    @Inject
    ObjectMapper objectMapper;

    public PreviewResponseDTO preview(ConnectorConfigDTO config) {
        return preview(config, true);
    }

    public PreviewResponseDTO previewPivot(ConnectorConfigDTO config) {
        return preview(config, false);
    }


    private PreviewResponseDTO preview(ConnectorConfigDTO config, boolean applyTransformers) {
        // The source-only preview walks no step but still samples like the full one (whole patients
        // when a patient transformation exists), so "Specify headers" shows the rows the steps get.
        List<ConnectorTransformerDTO> transformers = config.getTransformer() == null
                ? List.of()
                : config.getTransformer();

        PreviewResponseDTO response = new PreviewResponseDTO();
        response.setJsons(new ArrayList<>());
        response.setStages(new ArrayList<>());

        ConnectorPreviewTransformationStageData stage =
                walk(config, transformers, applyTransformers ? size(transformers) : 0, response, false, null)
                        .getStage();

        if (response.getJsons().isEmpty()) {
            response.getJsons().add(render(stage));
        }

        return response;
    }

    public ConnectorPreviewAppStageInput prepareAppStage(ConnectorConfigDTO config, int stepIndex) {
        List<ConnectorTransformerDTO> transformers = config.getTransformer();
        if (stepIndex < 1 || stepIndex > size(transformers)) {
            throw new BadRequestException("There is no transformation step " + stepIndex + " to run");
        }
        ConnectorTransformerDTO transformer = transformers.get(stepIndex - 1);
        if (!isAppBased(transformer)) {
            throw new BadRequestException("Transformation step " + stepIndex + " is not app based");
        }

        ConnectorPreviewWalk walk = walk(config, transformers, stepIndex - 1, null, true, null);
        return new ConnectorPreviewAppStageInput(
                transformer,
                walk.getStage().getRows(),
                walk.getStage().getColumns(),
                cacheBO.stageFingerprint(walk.getFingerprint(), transformer));
    }

    public void warmFromRun(ConnectorConfigDTO config,
                            BiFunction<ConnectorTransformerDTO, List<Map<String, Object>>, List<Map<String, Object>>> appRunner) {
        List<ConnectorTransformerDTO> transformers = config.getTransformer();
        if (config.getConnectorId() == null || size(transformers) == 0) {
            return;
        }
        walk(config, transformers, size(transformers), null, false, appRunner);
    }

    private ConnectorPreviewWalk walk(ConnectorConfigDTO config,
                                      List<ConnectorTransformerDTO> transformers,
                                      int upTo,
                                      PreviewResponseDTO response,
                                      boolean requireResult,
                                      BiFunction<ConnectorTransformerDTO, List<Map<String, Object>>, List<Map<String, Object>>> appRunner) {
        boolean patientPreview = hasPatientTransformation(transformers);
        String patientIdColumn = patientPreview ? requirePatientIdColumn(config) : null;

        Long connectorId = config.getConnectorId();
        ConnectorPreviewTransformationStageData stage =
                loadSourceStage(config, connectorId, cacheBO.sourceFingerprint(config), patientPreview, patientIdColumn);
        String fingerprint = cacheBO.sampleFingerprint(limitPreviewRows(stage.getRows()), stage.getColumns());

        Integer blockedBy = null;

        for (int index = 0; index < upTo; index++) {
            ConnectorTransformerDTO transformer = transformers.get(index);
            int stepIndex = index + 1;
            boolean appBased = isAppBased(transformer);
            fingerprint = cacheBO.stageFingerprint(fingerprint, transformer);
            Optional<ConnectorPreviewTransformationCacheStage> cached = blockedBy == null
                    ? cacheBO.find(connectorId, stepIndex, fingerprint)
                    : Optional.empty();
            if (cached.isPresent()) {
                Log.debugf("Preview stage %d of connector %d served from cache", stepIndex, connectorId);
                stage = new ConnectorPreviewTransformationStageData(
                        cached.get().getRows(), columnsOf(cached.get(), stage), stage.getProfiles());
                addStage(response, stepIndex, transformer, true, fingerprint,
                        cached.get().getCachedAt(), stage.getRows(), appBased, false, null);
            } else if (appBased && appRunner != null && blockedBy == null) {
                List<Map<String, Object>> rows = appRunner.apply(transformer, stage.getRows());
                List<String> columns = columnsOf(stage.getColumns(), rows);
                storeAppStage(connectorId, stepIndex, transformer, fingerprint, rows, columns);
                stage = new ConnectorPreviewTransformationStageData(rows, columns, stage.getProfiles());
            } else if (appBased) {
                // A preview never starts a container: the request would block for as long as the app
                // takes and the transaction reaper would abort it underneath. The wizard starts the
                // app itself, so until it has, the step shows its input and says so.
                if (requireResult) {
                    throw new BadRequestException("Transformation step " + stepIndex + " is app based and has"
                            + " not produced a result yet. Run it before running the steps after it.");
                }
                Log.debugf("App transformer at step %d has no preview result yet", stepIndex);
                addStage(response, stepIndex, transformer, false, fingerprint, null,
                        stage.getRows(), true, true, blockedBy);
                if (blockedBy == null) {
                    blockedBy = stepIndex;
                }
            } else {
                List<Map<String, Object>> rows =
                        transformerBO.applyBuiltInTransformation(transformer, stage.getRows(), patientIdColumn);
                stage = new ConnectorPreviewTransformationStageData(rows, stage.getColumns(), stage.getProfiles());
                if (blockedBy == null) {
                    cacheBO.store(connectorId, stepIndex, transformer.getId(), fingerprint,
                            limitPreviewRows(rows), stage.getColumns());
                }
                addStage(response, stepIndex, transformer, false, fingerprint, null, rows, false, false, blockedBy);
            }

            if (response != null) {
                response.getJsons().add(render(stage));
            }
        }

        return new ConnectorPreviewWalk(stage, fingerprint);
    }


    public void storeAppStage(Long connectorId,
                              int stepIndex,
                              ConnectorTransformerDTO transformer,
                              String fingerprint,
                              List<Map<String, Object>> rows,
                              List<String> columns) {
        cacheBO.store(connectorId, stepIndex, transformer.getId(), fingerprint,
                limitPreviewRows(rows), columns);
        cacheBO.invalidateFrom(connectorId, stepIndex + 1);
    }

    private String render(ConnectorPreviewTransformationStageData stage) {
        try (TableData staged = new TableData(
                stage.getColumns(), limitPreviewRows(stage.getRows()), stage.getProfiles())) {
            return fileHandlerBO.getJson(staged);
        }
    }

    private static void addStage(PreviewResponseDTO response,
                                 int stepIndex,
                                 ConnectorTransformerDTO transformer,
                                 boolean cached,
                                 String fingerprint,
                                 Date cachedAt,
                                 List<Map<String, Object>> rows,
                                 boolean appBased,
                                 boolean requiresRun,
                                 Integer blockedByStep) {
        if (response == null) {
            return;
        }
        response.getStages().add(describe(stepIndex, transformer, cached, fingerprint, cachedAt, rows,
                appBased, requiresRun, blockedByStep));
    }

    private static List<String> columnsOf(List<String> columns, List<Map<String, Object>> rows) {
        LinkedHashSet<String> all = new LinkedHashSet<>(columns == null ? List.of() : columns);
        rows.forEach(row -> all.addAll(row.keySet()));
        return new ArrayList<>(all);
    }

    private static List<String> columnsOf(ConnectorPreviewTransformationCacheStage cached,
                                          ConnectorPreviewTransformationStageData stage) {
        List<String> columns = cached.getColumns();
        return columns == null || columns.isEmpty() ? stage.getColumns() : columns;
    }

    static boolean isAppBased(ConnectorTransformerDTO transformer) {
        return transformer != null && transformer.getAppImage() != null && !transformer.getAppImage().isBlank();
    }

    private static int size(List<ConnectorTransformerDTO> transformers) {
        return transformers == null ? 0 : transformers.size();
    }

    private ConnectorPreviewTransformationStageData loadSourceStage(ConnectorConfigDTO config,
                                                                    Long connectorId,
                                                                    String fingerprint,
                                                                    boolean patientPreview,
                                                                    String patientIdColumn) {
        Optional<ConnectorPreviewTransformationCacheStage> cached = cacheBO.find(
                connectorId, ConnectorPreviewTransformationCacheBO.SOURCE_STEP, fingerprint);
        if (cached.isPresent()) {
            Log.debugf("Preview source sample of connector %d served from cache", connectorId);
            return new ConnectorPreviewTransformationStageData(cached.get().getRows(), cached.get().getColumns(), List.of());
        }

        TableData data = extractBO.loadPreviewData(
                config.getInputConfig(),
                config.getCohortId(),
                DEFAULT_PREVIEW_ROWS,
                config.getMergeConfig(),
                config.getPivotConfig(),
                uploadInfoOf(config)
        );
        try {
            if (data == null || data.longSize() == 0) {
                throw new BadRequestException("File not found or empty");
            }
            List<Map<String, Object>> rows = patientPreview
                    ? selectPatientPreviewRows(data, patientIdColumn)
                    : data.getRows();
            ConnectorPreviewTransformationStageData stage = new ConnectorPreviewTransformationStageData(rows, data.getColumns(), data.getColumnProfiles());
            cacheBO.store(connectorId, ConnectorPreviewTransformationCacheBO.SOURCE_STEP, null,
                    fingerprint, limitPreviewRows(rows), data.getColumns());
            return stage;
        } finally {
            if (data != null) {
                data.close();
            }
        }
    }


    private Map<String, UploadInfoDTO> uploadInfoOf(ConnectorConfigDTO config) {
        Object fileInfo = config.getFileInfo();
        if (fileInfo == null) {
            return null;
        }
        ObjectMapper lenient = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JsonNode node = lenient.valueToTree(fileInfo);
        if (node == null || !node.isObject()) {
            return null;
        }
        Map<String, UploadInfoDTO> uploadInfo = new LinkedHashMap<>();
        if (node.has("columns")) {
            // A single table: the merged one when sheets are merged, else the only/first sheet.
            String table = SheetMergePlan.requested(config.getMergeConfig())
                    ? SheetMergePlan.MERGED_TABLE_NAME
                    : ConnectorFileUploadInfoDTO.DEFAULT_SHEET_NAME;
            uploadInfo.put(table, lenient.convertValue(node, UploadInfoDTO.class));
        } else {
            node.fields().forEachRemaining(table -> {
                if (table.getValue().isObject()) {
                    uploadInfo.put(table.getKey(), lenient.convertValue(table.getValue(), UploadInfoDTO.class));
                }
            });
        }
        return uploadInfo.isEmpty() ? null : uploadInfo;
    }

    private static PreviewStageDTO describe(int stepIndex,
                                            ConnectorTransformerDTO transformer,
                                            boolean cached,
                                            String fingerprint,
                                            Date cachedAt,
                                            List<Map<String, Object>> rows,
                                            boolean appBased,
                                            boolean requiresRun,
                                            Integer blockedByStep) {
        PreviewStageDTO stage = new PreviewStageDTO();
        stage.setStepIndex(stepIndex);
        stage.setCached(cached);
        stage.setFingerprint(fingerprint);
        stage.setCachedAt(cachedAt);
        stage.setRowCount(rows == null ? 0 : rows.size());
        stage.setAppBased(appBased);
        stage.setRequiresRun(requiresRun);
        stage.setBlockedByStep(blockedByStep);
        if (transformer != null) {
            stage.setTransformerId(transformer.getId());
            stage.setTransformerName(transformer.getMethodName());
        }
        return stage;
    }

    List<Map<String, Object>> selectPatientPreviewRows(TableData data, String patientIdColumn) {
        if (patientIdColumn == null || patientIdColumn.isBlank()) {
            throw new BadRequestException(
                    "Patient transformations require a source column mapped to the external patient id"
            );
        }

        Map<String, List<Map<String, Object>>> selectedPatients = new LinkedHashMap<>();
        try (Stream<Map<String, Object>> stream = data.streamRows()) {
            stream.forEach(row -> {
                String patientId = requirePatientId(row, patientIdColumn);
                List<Map<String, Object>> patientRows = selectedPatients.get(patientId);
                if (patientRows != null) {
                    patientRows.add(row);
                } else if (selectedPatients.size() < DEFAULT_PREVIEW_PATIENTS) {
                    selectedPatients.put(patientId, new ArrayList<>(List.of(row)));
                }
            });
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        selectedPatients.values().forEach(rows::addAll);
        return rows;
    }

    private boolean hasPatientTransformation(List<ConnectorTransformerDTO> transformers) {
        if (transformers == null || transformers.isEmpty()) {
            return false;
        }
        return transformers.stream()
                .filter(transformer -> transformer.getAppImage() == null || transformer.getAppImage().isBlank())
                .anyMatch(transformer -> functionRunnerBO.mode(transformer) == FunctionExecutionMode.PATIENT);
    }

    private String requirePatientIdColumn(ConnectorConfigDTO config) {
        String patientIdColumn = mappingBO.resolveExternalIdSourceColumn(config.getSchemaMapping());
        if (patientIdColumn == null || patientIdColumn.isBlank()) {
            throw new BadRequestException(
                    "Patient transformations require a source column mapped to the external patient id"
            );
        }
        return patientIdColumn;
    }

    private String requirePatientId(Map<String, Object> row, String patientIdColumn) {
        if (row == null) {
            throw new BadRequestException("Preview contains a null patient row");
        }
        Object patientId = row.get(patientIdColumn);
        if (patientId == null || String.valueOf(patientId).isBlank()) {
            throw new BadRequestException(
                    "Preview row is missing the external patient id in column '" + patientIdColumn + "'"
            );
        }
        return String.valueOf(patientId);
    }

    private List<Map<String, Object>> limitPreviewRows(List<Map<String, Object>> rows) {
        if (rows.size() <= DEFAULT_PREVIEW_ROWS) {
            return rows;
        }
        return new ArrayList<>(rows.subList(0, DEFAULT_PREVIEW_ROWS));
    }
}
