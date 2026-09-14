package bio.cosy.feddb.local.api.cohort.patient.export;

import bio.cosy.feddb.core.api.project.PatientDataPivotDuplicatePolicy;
import bio.cosy.feddb.core.api.project.PatientDataPivotJoinField;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryExportDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.util.*;

@ApplicationScoped
public class PatientDataExportTransformerBO {
    public List<Map<String, Object>> formatDataEntries(
            List<PatientDataEntryExportDTO> dataEntries,
            boolean wideFormat,
            EnumSet<PatientDataPivotJoinField> joinFields,
            PatientDataPivotDuplicatePolicy duplicatePolicy
    ) {
        return formatDataEntries(dataEntries, wideFormat, joinFields, duplicatePolicy, List.of());
    }

    public List<Map<String, Object>> formatDataEntries(
            List<PatientDataEntryExportDTO> dataEntries,
            boolean wideFormat,
            EnumSet<PatientDataPivotJoinField> joinFields,
            PatientDataPivotDuplicatePolicy duplicatePolicy,
            List<String> orderedFeatureNames
    ) {
        if (dataEntries.isEmpty()) {
            throw new NotFoundException("No data entries found for the given patient");
        }
        PatientDataPivot.Options options = new PatientDataPivot.Options(
                normalizeJoinFields(joinFields),
                duplicatePolicy != null ? duplicatePolicy : PatientDataPivotDuplicatePolicy.ERROR
        );
        if (wideFormat) {
            return longFormatToWideFormat(dataEntries, options, orderedFeatureNames);
        } else {
            return toConfiguredLongFormat(dataEntries, options);
        }
    }

    public List<Map<String, Object>> longFormatToWideFormat(
            List<PatientDataEntryExportDTO> longRows,
            PatientDataPivot.Options options
    ) {
        return longFormatToWideFormat(longRows, options, List.of());
    }

    public List<Map<String, Object>> longFormatToWideFormat(
            List<PatientDataEntryExportDTO> longRows,
            PatientDataPivot.Options options,
            List<String> orderedFeatureNames
    ) {
        if (longRows == null || longRows.isEmpty()) {
            return List.of();
        }
        if (options == null) {
            options = new PatientDataPivot.Options();
        }
        options = new PatientDataPivot.Options(
                normalizeJoinFields(options.getJoinFields()),
                options.getDuplicatePolicy() != null ? options.getDuplicatePolicy() : PatientDataPivotDuplicatePolicy.ERROR
        );

        Map<PatientDataPivot.GroupKey, List<LinkedHashMap<String, Object>>> grouped = new LinkedHashMap<>();

        for (PatientDataEntryExportDTO dto : longRows) {
            PatientDataPivot.GroupKey key = PatientDataPivot.GroupKey.of(dto, options.getJoinFields());

            PatientDataPivot.Options finalOptions = options;
            List<LinkedHashMap<String, Object>> groupRows = grouped.computeIfAbsent(key, k -> new ArrayList<>());
            String col = getFeatureName(dto);
            mergeValue(groupRows, dto, col, options, finalOptions, orderedFeatureNames);
        }

        return grouped.values().stream()
                .flatMap(Collection::stream)
                .<Map<String, Object>>map(row -> row)
                .toList();
    }

    private List<Map<String, Object>> toConfiguredLongFormat(
            List<PatientDataEntryExportDTO> dataEntries,
            PatientDataPivot.Options options
    ) {
        return dataEntries.stream()
                .<Map<String, Object>>map(dto -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    putJoinColumns(row, dto, options);
                    row.put("feature_name", getFeatureName(dto));
                    row.put("feature_value", dto.getValue());
                    return row;
                })
                .toList();
    }

    private void putJoinColumns(Map<String, Object> row, PatientDataEntryExportDTO dto, PatientDataPivot.Options options) {
        EnumSet<PatientDataPivotJoinField> jf = options.getJoinFields();
        if (jf.contains(PatientDataPivotJoinField.PATIENT_ID))
            row.put("patient_id", dto.getPatientId());
        if (jf.contains(PatientDataPivotJoinField.VISIT_ID))
            row.put("visit_id", dto.getVisitId());
        if (jf.contains(PatientDataPivotJoinField.VISIT_TIMESTAMP))
            row.put("visit_timestamp", dto.getVisitTimestamp());
        if (jf.contains(PatientDataPivotJoinField.IMPORT_SCHEMA_GROUP_ID))
            row.put("import_schema_group_id", dto.getImportSchemaGroupId());
        if (jf.contains(PatientDataPivotJoinField.VISIT_TIMESTAMP_FORMAT))
            row.put("visit_timestamp_format", dto.getVisitTimestampFormat());
    }

    private void mergeValue(
            List<LinkedHashMap<String, Object>> groupRows,
            PatientDataEntryExportDTO dto,
            String col,
            PatientDataPivot.Options options,
            PatientDataPivot.Options finalOptions,
            List<String> orderedFeatureNames
    ) {
        Optional<LinkedHashMap<String, Object>> openRow = findFirstOpenRow(groupRows, col);
        if (openRow.isPresent()) {
            openRow.get().put(col, dto.getValue());
            return;
        }
        if (groupRows.isEmpty()) {
            createWideRow(groupRows, dto, finalOptions, orderedFeatureNames).put(col, dto.getValue());
            return;
        }

        switch (options.getDuplicatePolicy()) {
            case KEEP_FIRST -> {
                // do nothing
            }
            case KEEP_LAST -> groupRows.get(0).put(col, dto.getValue());
            case ERROR -> createWideRow(groupRows, dto, finalOptions, orderedFeatureNames).put(col, dto.getValue());
        }
    }

    private Optional<LinkedHashMap<String, Object>> findFirstOpenRow(
            List<LinkedHashMap<String, Object>> groupRows,
            String col
    ) {
        return groupRows.stream()
                .filter(row -> !row.containsKey(col) || row.get(col) == null)
                .findFirst();
    }

    private LinkedHashMap<String, Object> createWideRow(
            List<LinkedHashMap<String, Object>> groupRows,
            PatientDataEntryExportDTO dto,
            PatientDataPivot.Options options,
            List<String> orderedFeatureNames
    ) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        putJoinColumns(row, dto, options);
        for (String featureName : orderedFeatureNames) {
            row.put(featureName, null);
        }
        groupRows.add(row);
        return row;
    }

    private EnumSet<PatientDataPivotJoinField> normalizeJoinFields(EnumSet<PatientDataPivotJoinField> joinFields) {
        if (joinFields == null || joinFields.isEmpty()) {
            return new PatientDataPivot.Options().getJoinFields();
        }
        return EnumSet.copyOf(joinFields);
    }

    private String getFeatureName(PatientDataEntryExportDTO dto) {
        return dto.getName() != null && !dto.getName().isBlank()
                ? dto.getName()
                : dto.getOntologyId() + "@" + dto.getDatatypeId();
    }
}
