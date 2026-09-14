package bio.cosy.feddb.local.api.importer.statistics;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.extract.ConnectorExtractBO;
import bio.cosy.feddb.local.api.importer.extract.PivotConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.extract.UploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesAO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesEntity;
import bio.cosy.feddb.local.api.importer.files.read.TabularFileReaderBO;
import bio.cosy.feddb.local.api.importer.files.table.ColumnNames;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import bio.cosy.feddb.local.api.importer.functions.FunctionRunnerBO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.mapping.MappingBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Recomputes column categories/profiles <em>after</em> the built-in transformer pipeline has run,
 * so mapping and validation see the values a column actually holds once transformers modified or
 * created it — not the stale raw-file values.
 *
 * <p>This is deliberately not a full statistic: it runs the pipeline on the bounded preview rows
 * retained during file analysis (keeping whole preview patients for PATIENT-mode transformers) and
 * re-profiles the transformed rows. Untouched columns use their complete upload-time profiles in
 * {@code PreviewValidationBO}; only transformed values use this interactive sample.</p>
 *
 * <p>Remote app-based transformers require the execution subsystem and are not executed here; the
 * caller warns about columns they produce and lets the user add categories manually.</p>
 */
@ApplicationScoped
public class TransformedStatisticsBO {

    private static final int STATISTICS_TIMEOUT_SECONDS = 15 * 60;

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
    ConnectorFilesAO ao;

    @Inject
    ObjectMapper objectMapper;

    /** Upper bound on the number of representative rows the pipeline is run on. */
    @ConfigProperty(name = "connector.transformed-statistics.max-rows", defaultValue = "10000")
    int maxRows;

    /** Upper bound on the number of distinct patients selected for PATIENT-mode transformers. */
    @ConfigProperty(name = "connector.transformed-statistics.max-patients", defaultValue = "500")
    int maxPatients;

    /** Per-column cap on tracked distinct values, so high-cardinality columns don't blow up the sample. */
    @ConfigProperty(name = "connector.transformed-statistics.max-distinct-per-column", defaultValue = "200")
    int maxDistinctPerColumn;

    @Transactional
    @TransactionConfiguration(timeout = STATISTICS_TIMEOUT_SECONDS)
    public List<ColumnProfile> getTransformedStatistics(
            Long cohortId,
            ConnectorInputConfigDTO inputConfig,
            SheetMergeResultDTO mergeConfig,
            PivotConfigDTO pivotConfig,
            List<ConnectorTransformerDTO> transformers,
            List<ConnectorMappingDTO> schemaMapping) {
        return getTransformedStatistics(
                cohortId,
                inputConfig,
                null,
                mergeConfig,
                pivotConfig,
                transformers,
                schemaMapping
        );
    }

    @Transactional
    @TransactionConfiguration(timeout = STATISTICS_TIMEOUT_SECONDS)
    public List<ColumnProfile> getTransformedStatistics(
            Long cohortId,
            ConnectorInputConfigDTO inputConfig,
            Map<String, UploadInfoDTO> uploadInfo,
            SheetMergeResultDTO mergeConfig,
            PivotConfigDTO pivotConfig,
            List<ConnectorTransformerDTO> transformers,
            List<ConnectorMappingDTO> schemaMapping) {
        if (!(inputConfig instanceof FileUploadSettingsDTO fileSettings)) {
            throw new BadRequestException("Statistics are only supported for file input");
        }

        ConnectorFilesEntity fileEntity = resolveFile(cohortId, fileSettings);
        List<ConnectorTransformerDTO> builtInTransformers = builtInTransformers(transformers);
        boolean patientMode = hasPatientTransformation(builtInTransformers);
        String patientIdColumn = patientMode
                ? mappingBO.resolveExternalIdSourceColumn(schemaMapping)
                : null;

        String cacheKey = cacheKey(
                fileEntity,
                fileSettings,
                uploadInfo,
                mergeConfig,
                pivotConfig,
                builtInTransformers,
                patientIdColumn
        );
        if (Objects.equals(fileEntity.getTransformedStatisticsCacheKey(), cacheKey)
                && fileEntity.getTransformedStatistics() != null) {
            return fileEntity.getTransformedStatistics();
        }

        // Validation only needs representative transformed values. Reuse the bounded preview
        // captured during file analysis instead of decoding and merging the complete import again.
        // The latter can contain millions of rows and used to exhaust the request transaction before
        // the SSE stream could replace its pending column events with actual results.
        try (TableData source = extractBO.loadPreviewData(
                inputConfig, cohortId, maxRows, mergeConfig, pivotConfig, uploadInfo)) {
            if (source == null || source.longSize() == 0) {
                throw new NotFoundException("File not found or contains no readable table");
            }

            List<String> sourceColumns = source.getColumns() == null ? List.of() : source.getColumns();
            boolean usePatientSelection = patientMode && patientIdColumn != null && !patientIdColumn.isBlank();
            List<Map<String, Object>> rows = usePatientSelection
                    ? selectRepresentativePatients(source, sourceColumns, patientIdColumn)
                    : selectRepresentativeRows(source, sourceColumns);

            for (ConnectorTransformerDTO transformer : builtInTransformers) {
                rows = transformerBO.applyBuiltInTransformation(transformer, rows, patientIdColumn);
            }

            List<ColumnProfile> profiles;
            try (TableData transformed = new TableData(resultColumns(sourceColumns, rows), rows, null)) {
                profiles = fileHandlerBO.getColumnProfiles(transformed);
            }

            fileEntity.setTransformedStatisticsCacheKey(cacheKey);
            fileEntity.setTransformedStatistics(profiles);
            return profiles;
        }
    }

    /**
     * Returns the upload-time profiles for the first logical source table after applying the
     * request's column renames and deletions. No file rows are read, so this is suitable for the
     * fast path of preview validation.
     */
    @Transactional
    public List<ColumnProfile> getSourceStatistics(
            Long cohortId,
            ConnectorInputConfigDTO inputConfig,
            Map<String, UploadInfoDTO> uploadInfo
    ) {
        if (!(inputConfig instanceof FileUploadSettingsDTO fileSettings)) {
            throw new BadRequestException("Statistics are only supported for file input");
        }
        ConnectorFilesEntity fileEntity = resolveFile(cohortId, fileSettings);
        List<ConnectorFileUploadInfoDTO> stored = fileEntity.getUploadInfo();
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        ConnectorFileUploadInfoDTO first = stored.stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (first == null || first.getColumnProfiles() == null) {
            return List.of();
        }
        return projectProfiles(first.getColumnProfiles(), uploadInfoForTable(uploadInfo, first.getSheet()));
    }

    private List<ColumnProfile> projectProfiles(
            List<ColumnProfile> profiles,
            UploadInfoDTO configured
    ) {
        if (profiles == null || profiles.isEmpty()) {
            return List.of();
        }
        if (configured == null || configured.getColumns() == null || configured.getColumns().isEmpty()) {
            return profiles.stream().filter(Objects::nonNull).toList();
        }

        List<ColumnProfile> projected = new ArrayList<>();
        Set<String> targetColumns = new LinkedHashSet<>();
        for (ColumnProfile profile : profiles) {
            if (profile == null || profile.name() == null) {
                continue;
            }
            int index = configuredColumnIndex(configured.getColumns(), profile.name());
            boolean deleted = index >= 0
                    && configured.getDeletedColumns() != null
                    && index < configured.getDeletedColumns().size()
                    && Boolean.TRUE.equals(configured.getDeletedColumns().get(index));
            if (deleted) {
                continue;
            }
            String target = index >= 0
                    && configured.getRenamedColumns() != null
                    && index < configured.getRenamedColumns().size()
                    && configured.getRenamedColumns().get(index) != null
                    && !configured.getRenamedColumns().get(index).isBlank()
                    ? configured.getRenamedColumns().get(index)
                    : profile.name();
            if (!targetColumns.add(target)) {
                throw new BadRequestException(
                        "Upload column configuration produces duplicate column '" + target + "'");
            }
            projected.add(profile.withName(target));
        }
        return List.copyOf(projected);
    }

    private int configuredColumnIndex(List<String> configuredColumns, String sourceColumn) {
        for (int index = 0; index < configuredColumns.size(); index++) {
            String configuredColumn = configuredColumns.get(index);
            if (configuredColumn != null
                    && (configuredColumn.equals(sourceColumn)
                    || Objects.equals(ColumnNames.unqualify(configuredColumn), sourceColumn))) {
                return index;
            }
        }
        return -1;
    }

    private UploadInfoDTO uploadInfoForTable(Map<String, UploadInfoDTO> uploadInfo, String tableName) {
        if (uploadInfo == null || uploadInfo.isEmpty()) {
            return null;
        }
        UploadInfoDTO exact = uploadInfo.get(tableName);
        if (exact != null) {
            return exact;
        }
        String wanted = ColumnNames.table(ColumnNames.tableOfFile(tableName));
        return uploadInfo.entrySet().stream()
                .filter(entry -> ColumnNames.table(ColumnNames.tableOfFile(entry.getKey())).equals(wanted))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseGet(() -> uploadInfo.size() == 1
                        ? uploadInfo.getOrDefault(
                                ConnectorFileUploadInfoDTO.DEFAULT_SHEET_NAME,
                                uploadInfo.values().iterator().next())
                        : null);
    }

    /**
     * Selects rows covering, per column, up to {@link #maxDistinctPerColumn} distinct values.
     * A row is kept when it introduces a not-yet-seen value for at least one column that has not
     * reached its cap — a cheap "marginal cover" that keeps categorical columns complete while
     * bounding the sample.
     */
    private List<Map<String, Object>> selectRepresentativeRows(TableData source, List<String> columns) {
        Map<String, Set<String>> seenPerColumn = new HashMap<>();
        List<Map<String, Object>> selected = new ArrayList<>();
        try (Stream<Map<String, Object>> stream = source.streamRows()) {
            Iterator<Map<String, Object>> iterator = stream.iterator();
            while (iterator.hasNext() && selected.size() < maxRows) {
                Map<String, Object> row = iterator.next();
                if (introducesNewValue(row, columns, seenPerColumn)) {
                    selected.add(new LinkedHashMap<>(row));
                }
            }
        }
        return selected;
    }

    /**
     * Selects whole patients (all their rows) up to {@link #maxPatients}, preferring patients whose
     * first seen row introduces new column values. PATIENT-mode transformers aggregate across a
     * patient's rows, so partial patients would produce wrong values.
     */
    private List<Map<String, Object>> selectRepresentativePatients(
            TableData source, List<String> columns, String patientIdColumn) {
        Map<String, Set<String>> seenPerColumn = new HashMap<>();
        Map<String, List<Map<String, Object>>> selectedPatients = new LinkedHashMap<>();
        int rowCount = 0;

        try (Stream<Map<String, Object>> stream = source.streamRows()) {
            Iterator<Map<String, Object>> iterator = stream.iterator();
            while (iterator.hasNext() && rowCount < maxRows) {
                Map<String, Object> row = iterator.next();
                String patientId = stringValue(row.get(patientIdColumn));
                if (patientId.isBlank()) {
                    continue;
                }
                List<Map<String, Object>> patientRows = selectedPatients.get(patientId);
                if (patientRows != null) {
                    patientRows.add(new LinkedHashMap<>(row));
                    rowCount++;
                } else if (selectedPatients.size() < maxPatients
                        && introducesNewValue(row, columns, seenPerColumn)) {
                    List<Map<String, Object>> newPatient = new ArrayList<>();
                    newPatient.add(new LinkedHashMap<>(row));
                    selectedPatients.put(patientId, newPatient);
                    rowCount++;
                }
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>(rowCount);
        selectedPatients.values().forEach(rows::addAll);
        return rows;
    }

    private boolean introducesNewValue(
            Map<String, Object> row, List<String> columns, Map<String, Set<String>> seenPerColumn) {
        boolean introduces = false;
        for (String column : columns) {
            Set<String> seen = seenPerColumn.computeIfAbsent(column, ignored -> new HashSet<>());
            if (seen.size() >= maxDistinctPerColumn) {
                continue;
            }
            if (seen.add(stringValue(row.get(column)))) {
                introduces = true;
            }
        }
        return introduces;
    }

    /** Source columns first, then any new columns produced by transformers, preserving first-seen order. */
    private List<String> resultColumns(List<String> sourceColumns, List<Map<String, Object>> rows) {
        LinkedHashSet<String> columns = new LinkedHashSet<>(sourceColumns);
        for (Map<String, Object> row : rows) {
            columns.addAll(row.keySet());
        }
        return new ArrayList<>(columns);
    }

    private List<ConnectorTransformerDTO> builtInTransformers(List<ConnectorTransformerDTO> transformers) {
        if (transformers == null) {
            return List.of();
        }
        return transformers.stream()
                .filter(Objects::nonNull)
                .filter(transformer -> transformer.getAppImage() == null || transformer.getAppImage().isBlank())
                .toList();
    }

    private boolean hasPatientTransformation(List<ConnectorTransformerDTO> transformers) {
        return transformers.stream()
                .anyMatch(transformer -> functionRunnerBO.mode(transformer) == FunctionExecutionMode.PATIENT);
    }

    private ConnectorFilesEntity resolveFile(Long cohortId, FileUploadSettingsDTO fileSettings) {
        if (cohortId == null) {
            throw new BadRequestException("Cohort ID is required");
        }
        if (fileSettings.getFileId() != null) {
            return ao.getFileByCohortId(cohortId, fileSettings.getFileId())
                    .orElseThrow(() -> new NotFoundException("File not found for cohort"));
        }
        List<ConnectorFilesEntity> cohortFiles = ao.getFilesByCohort(cohortId);
        if (cohortFiles.isEmpty()) {
            throw new NotFoundException("No input file found for cohort");
        }
        return cohortFiles.stream()
                .filter(file -> !Boolean.TRUE.equals(file.getIsSupportFile()))
                .findFirst()
                .orElse(cohortFiles.getFirst());
    }

    /**
     * Cache key over everything that changes the transformed profiles: the file bytes, the parsing /
     * merge / pivot settings and the transformer pipeline. It deliberately excludes the schema
     * mapping, so simply re-mapping columns does not invalidate the cache.
     */
    private String cacheKey(
            ConnectorFilesEntity file,
            FileUploadSettingsDTO fileSettings,
            Map<String, UploadInfoDTO> uploadInfo,
            SheetMergeResultDTO mergeConfig,
            PivotConfigDTO pivotConfig,
            List<ConnectorTransformerDTO> transformers,
            String patientIdColumn) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("cacheVersion", 3);
        parameters.put("largeObjectId", file.getLargeObjectId());
        parameters.put("fileSettings", fileSettings);
        parameters.put("uploadInfo", uploadInfo);
        parameters.put("mergeConfig", mergeConfig);
        parameters.put("pivotConfig", pivotConfig);
        parameters.put("transformers", transformers);
        parameters.put("patientIdColumn", patientIdColumn);

        try {
            JsonNode canonical = canonicalize(objectMapper.valueToTree(parameters));
            byte[] serialized = objectMapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not build transformed-statistics cache key", exception);
        }
    }

    /** Recursively sorts object keys so semantically equal maps hash to the same key. */
    private static JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(child -> result.add(canonicalize(child)));
            return result;
        }
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        List<String> fieldNames = new ArrayList<>();
        node.fieldNames().forEachRemaining(fieldNames::add);
        fieldNames.stream().sorted().forEach(name -> result.set(name, canonicalize(node.get(name))));
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
