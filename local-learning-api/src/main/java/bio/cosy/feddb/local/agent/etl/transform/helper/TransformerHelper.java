package bio.cosy.feddb.local.agent.etl.transform.helper;

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
    public static boolean hasSeveralRowsPerPatient(TableSample preview, String patientIdColumn) {
        if (patientIdColumn == null) {
            return false;
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
