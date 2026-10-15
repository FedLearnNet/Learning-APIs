package bio.cosy.feddb.local.agent.etl.transform.helper;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.VisitMapping;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public class TransformerHelper {

    private static final int EXAMPLE_VALUES = 3;
    private static final int MAX_FILE_VALUES = 50;
    private static final int DESCRIBED_FILE_VALUES = 30;

    public static ConnectorDTO toConnector(Collection<TransformerAssistantMessageDTO> results) {
        List<ConnectorTransformerDTO> transformers = new ArrayList<>();
        List<ConnectorMappingDTO> mappings = new ArrayList<>();
        for (TransformerAssistantMessageDTO result : results) {
            if (result.getType() == TransformerAssistantMessageType.PATIENT_ID) {
                ConnectorMappingDTO mapping = new ConnectorMappingDTO();
                mapping.setColumn(result.getColumns().getFirst());
                mapping.setMapping(result.getMappingPath());
                mappings.add(mapping);
                continue;
            }
            ConnectorSelectedTransformer selected = result.getSelectedTransformer();
            if (selected == null || !selected.isFoundTransformer()) {
                continue;
            }
            transformers.addAll(selected.getSelectedTransformers());
            ConnectorMappingDTO mapping = new ConnectorMappingDTO();
            mapping.setColumn(selected.getMappedColumn());
            mapping.setMapping(result.getMappingPath());
            mapping.setSchemaId(result.getSchemaId());
            Optional.ofNullable(result.getVisitMapping()).ifPresent(visit -> {
                mapping.setVisitIdMapping(visit.getVisitIdMapping());
                mapping.setVisitTimestampMapping(visit.getVisitTimestampMapping());
                mapping.setTimestampFormat(visit.getTimestampFormat());
            });
            mappings.add(mapping);
        }
        ConnectorDTO connector = new ConnectorDTO();
        connector.setTransformer(transformers);
        connector.setSchemaMapping(mappings);
        return connector;
    }

    /** Whether a patient has several rows, e.g. one per encounter, so the rows need a visit to be told apart. */
    public static boolean hasSeveralRowsPerPatient(TableSample preview, String patientIdColumn,
                                                   Map<String, ColumnProfile> fileProfiles) {
        if (patientIdColumn == null) {
            return false;
        }
        // The preview holds only the first rows, the profile counts the patients of the whole file
        ColumnProfile profile = fileProfiles.get(patientIdColumn);
        if (profile != null && profile.uniqueValues() > 0) {
            return profile.uniqueValues() < profile.count() - profile.missing();
        }
        List<Object> patientIds = preview.getRows().stream()
                .filter(Objects::nonNull)
                .map(row -> row.get(patientIdColumn))
                .filter(Objects::nonNull)
                .toList();
        return patientIds.stream().distinct().count() < patientIds.size();
    }

    /** Every column with a few of its values, e.g. "encounter_id: 2278392, 149190, 64410". */
    public static List<String> columnExamples(TableSample preview) {
        return preview.getColumns().stream()
                .map(column -> column + ": " + preview.getRows().stream()
                        .filter(Objects::nonNull)
                        .map(row -> Objects.toString(row.get(column), ""))
                        .filter(value -> !value.isBlank())
                        .distinct()
                        .limit(EXAMPLE_VALUES)
                        .collect(Collectors.joining(", ")))
                .toList();
    }

    public static List<String> fileValues(ColumnProfile profile) {
        if (profile == null) {
            return List.of();
        }
        List<Map.Entry<String, Integer>> counts = valueCounts(profile);
        List<String> values = counts.stream()
                .map(Map.Entry::getKey)
                .filter(value -> value != null && !value.isBlank())
                .limit(MAX_FILE_VALUES)
                .collect(Collectors.toCollection(ArrayList::new));
        List<Map.Entry<String, Integer>> markers = missingValues(profile);
        markers.forEach(marker -> values.add(marker.getKey()));
        if (profile.missing() > markers.stream().mapToLong(Map.Entry::getValue).sum()) {
            values.add(null);
        }
        return values;
    }

    public static List<Map<String, Object>> valueRows(TableSample preview, Collection<String> columns,
                                                      Map<String, ColumnProfile> fileProfiles, String patientIdColumn) {
        Map<String, Object> base = preview.getRows().stream().filter(Objects::nonNull).findFirst().orElse(Map.of());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String column : columns) {
            for (String value : fileValues(fileProfiles.get(column))) {
                Map<String, Object> row = new LinkedHashMap<>(base);
                if (patientIdColumn != null && !patientIdColumn.equals(column)) {
                    // Every value row is its own patient, so PATIENT transformers do not mix them
                    row.put(patientIdColumn, "value-row-" + rows.size());
                }
                row.put(column, value);
                rows.add(row);
            }
        }
        return rows;
    }

    public static String describeFileValues(Collection<String> columns, Map<String, ColumnProfile> fileProfiles) {
        return columns.stream()
                .filter(fileProfiles::containsKey)
                .map(column -> column + " " + describeFileValues(fileProfiles.get(column), DESCRIBED_FILE_VALUES))
                .collect(Collectors.joining("\n"));
    }

    public static String describeFileValues(ColumnProfile profile, int maxValues) {
        StringBuilder text = new StringBuilder("(").append(profile.count()).append(" rows, ")
                .append(profile.missing()).append(" empty");
        List<Map.Entry<String, Integer>> markers = missingValues(profile);
        if (!markers.isEmpty()) {
            text.append(" or a missing-value marker ").append(counted(markers, markers.size()));
        }
        if (profile.min() != null && profile.max() != null) {
            text.append(", ").append(profile.min()).append(" to ").append(profile.max());
        }
        text.append(')');
        List<Map.Entry<String, Integer>> counts = valueCounts(profile);
        if (counts.isEmpty()) {
            return text.toString();
        }
        text.append(": ").append(counted(counts, maxValues));
        if (counts.size() > maxValues) {
            text.append(", ... ").append(profile.uniqueValues()).append(" distinct values");
        }
        return text.toString();
    }

    private static String counted(List<Map.Entry<String, Integer>> counts, int maxValues) {
        return counts.stream()
                .limit(maxValues)
                .map(entry -> "'" + entry.getKey() + "' x" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    public static List<Map.Entry<String, Integer>> missingValues(ColumnProfile profile) {
        return profile.missingValues() == null ? List.of() : profile.missingValues();
    }

    public static List<Map.Entry<String, Integer>> valueCounts(ColumnProfile profile) {
        List<Map.Entry<String, Integer>> counts = profile.valueCounts() != null ? profile.valueCounts() : profile.topCategories();
        return counts == null ? List.of() : counts.stream().filter(Objects::nonNull).toList();
    }

    /**
     * The visit mapping without what the import could not use: unknown columns, the patient id column as visit id
     * and a timestamp format that does not parse the sample values. Null when nothing is left.
     */
    public static VisitMapping checkVisitMapping(VisitMapping visit, TableSample preview, String patientIdColumn) {
        if (visit == null) {
            return null;
        }
        String visitId = isColumn(visit.getVisitIdMapping(), preview) && !visit.getVisitIdMapping().equals(patientIdColumn)
                ? visit.getVisitIdMapping() : null;
        String timestamp = isColumn(visit.getVisitTimestampMapping(), preview) ? visit.getVisitTimestampMapping() : null;
        String format = timestamp != null && parsesAll(visit.getTimestampFormat(), preview, timestamp)
                ? visit.getTimestampFormat() : null;
        return visitId == null && timestamp == null ? null : new VisitMapping(visitId, timestamp, format);
    }

    private static boolean isColumn(String column, TableSample preview) {
        return column != null && preview.getColumns().contains(column);
    }

    /** Parses like the import does it, see PatientMapper#normalizeVisitTimestamp. */
    private static boolean parsesAll(String format, TableSample preview, String column) {
        if (format == null || format.isBlank()) {
            return false;
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            boolean withTime = format.contains("H") || format.contains("m") || format.contains("s");
            preview.getRows().stream()
                    .filter(Objects::nonNull)
                    .map(row -> Objects.toString(row.get(column), ""))
                    .filter(value -> !value.isBlank())
                    .forEach(value -> {
                        if (withTime) {
                            LocalDateTime.parse(value, formatter);
                        } else {
                            LocalDate.parse(value, formatter);
                        }
                    });
            return true;
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return false;
        }
    }
}
