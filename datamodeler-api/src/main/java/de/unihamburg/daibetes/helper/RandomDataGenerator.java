package de.unihamburg.daibetes.helper;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeValueGenerator;
import de.unihamburg.daibetes.api.validation.ValidationBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.*;

@ApplicationScoped
public class RandomDataGenerator {

    @Inject
    ValidationBO validationBO;

    private final Random random = new SecureRandom();

    public List<String> generateDummyData(
            DataTypeNodeDTO dataType,
            int amount,
            int maxAttempts
    ) {
        List<String> generated = new ArrayList<>(amount);

        for (int i = 0; i < amount; i++) {
            Object value = DataTypeValueGenerator.randomValue(dataType, random);
            // Retry values the validations of the data type reject, e.g. a pattern the generator does not know
            for (int attempts = 0; attempts < maxAttempts && !validationBO.normalizeValidateValueSilent(value, dataType); attempts++) {
                value = DataTypeValueGenerator.randomValue(dataType, random);
            }
            generated.add(value.toString());
        }

        return generated;
    }

    public List<Map<String, String>> wideFormatToLongFormat(List<Map<String, String>> data) {
        List<Map<String, String>> longRows = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return longRows;
        }

        int maxPatientIds = Math.max(1, data.size() / 5);

        for (Map<String, String> row : data) {
            long patientId = DataTypeValueGenerator.randomLong(1, maxPatientIds, random);
            long visitId = DataTypeValueGenerator.randomLong(1_000, 999_999, random);
            String visitTs = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(DataTypeValueGenerator.randomDateTime(random)) + "Z";
            String visitTsFmt = "ISO-8601";

            for (Map.Entry<String, String> entry : row.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                Map<String, String> out = new LinkedHashMap<>();
                out.put("id", DataTypeValueGenerator.randomLong(1, 1_000_000_000L, random) + "");
                out.put("patientId", patientId + "");
                out.put("name", key);
                out.put("value", value);
                out.put("visitId", visitId + "");
                out.put("visitTimestamp", visitTs);
                out.put("visitTimestampFormat", visitTsFmt);

                longRows.add(out);
            }
        }

        return longRows;
    }

    public List<Map<String, String>> createListOfDicts(Map<String, List<String>> data) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (data.isEmpty()) {
            return rows;
        }

        int amount = data.values().stream()
                .findFirst()
                .map(List::size)
                .orElse(0);

        for (int i = 0; i < amount; i++) {
            Map<String, String> row = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : data.entrySet()) {
                List<String> values = entry.getValue();
                String value = i < values.size() ? values.get(i) : null;
                row.put(entry.getKey(), value);
            }
            rows.add(row);
        }
        return rows;
    }

    public String toCsv(List<Map<String, String>> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }

        Map<String, String> first = data.getFirst();
        List<String> headers = new ArrayList<>(first.keySet());

        StringBuilder sb = new StringBuilder();

        sb.append(String.join(",", headers)).append("\n");

        for (Map<String, String> row : data) {
            List<String> values = new ArrayList<>(headers.size());
            for (String header : headers) {
                Object value = row.get(header);
                values.add(escapeCsv(value));
            }
            sb.append(String.join(",", values)).append("\n");
        }

        return sb.toString();
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        boolean needsQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (needsQuotes) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }
}
