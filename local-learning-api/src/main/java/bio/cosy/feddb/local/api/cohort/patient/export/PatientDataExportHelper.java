package bio.cosy.feddb.local.api.cohort.patient.export;

import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryExportDTO;
import jakarta.ws.rs.core.StreamingOutput;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class PatientDataExportHelper {

    public static List<Map<String, Object>> toListMap(List<PatientDataEntryExportDTO> dataEntries) {
        return dataEntries.stream()
                .map(dto -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", dto.getId());
                    map.put("patientId", dto.getPatientId());
                    if (dto.getName() != null && !dto.getName().isBlank()) {
                        map.put("name", dto.getName());
                    } else {
                        map.put("ontologyId", dto.getOntologyId());
                        map.put("datatypeId", dto.getDatatypeId());
                    }
                    map.put("value", dto.getValue());
                    map.put("visitId", dto.getVisitId());
                    map.put("importSchemaGroupId", dto.getImportSchemaGroupId());
                    map.put("visitTimestamp", dto.getVisitTimestamp());
                    map.put("visitTimestampFormat", dto.getVisitTimestampFormat());
                    return map;
                })
                .toList();
    }

    public static StreamingOutput convertToCSVStream(List<Map<String, Object>> exported) {
        return out -> {
            try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                if (exported == null || exported.isEmpty()) {
                    writer.write("No data available");
                    return;
                }
                Set<String> columns = new LinkedHashSet<>();
                for (Map<String, Object> row : exported) {
                    columns.addAll(row.keySet());
                }
                try (CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
                    csvPrinter.printRecord(columns);
                    for (Map<String, Object> row : exported) {
                        List<String> record = new ArrayList<>();
                        for (String col : columns) {
                            Object value = row.get(col);
                            record.add(value != null ? value.toString() : "");
                        }
                        csvPrinter.printRecord(record);
                    }
                }
            }
        };
    }

    public static Path writeStreamToTempFile(List<PatientDataEntryExportDTO> dataEntries) {
        StreamingOutput stream = convertToCSVStream(toListMap(dataEntries));
        return writeStreamToTempFile(stream);
    }

    public static Path writeExportedRowsToTempFile(List<Map<String, Object>> exported) {
        StreamingOutput stream = convertToCSVStream(exported);
        return writeStreamToTempFile(stream);
    }

    public static Path writeStreamToTempFile(StreamingOutput stream) {
        try {
            Path tempFile = java.nio.file.Files.createTempFile("patient_data_export_", ".csv");
            try (java.io.OutputStream outputStream = java.nio.file.Files.newOutputStream(tempFile)) {
                stream.write(outputStream);
            }
            return tempFile;
        } catch (Exception e) {
            throw new RuntimeException("Error writing stream to temp file", e);
        }
    }
}
