package de.unihamburg.daibetes.helper;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.validation.DataTypeValidationDTO;
import de.unihamburg.daibetes.api.validation.ValidationBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@ApplicationScoped
public class RandomDataGenerator {

    @Inject
    ValidationBO validationBO;

    private final Random random = new SecureRandom();
    private final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public List<String> generateDummyData(
            DataTypeNodeDTO dataType,
            int amount,
            int maxAttempts
    ) {
        List<String> generated = new ArrayList<>(amount);

        for (int i = 0; i < amount; i++) {
            Object value = generateRandomValue(dataType);
            int attempts = 0;

            boolean b = !validationBO.normalizeValidateValueSilent(value, dataType);
            while (!b && attempts < maxAttempts) {
                value = generateRandomValue(dataType);
                attempts++;
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
            long patientId = randomLong(1, maxPatientIds);
            long visitId = randomLong(1_000, 999_999);
            String visitTs = generateRandomInstantIso();
            String visitTsFmt = "ISO-8601";

            for (Map.Entry<String, String> entry : row.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                Map<String, String> out = new LinkedHashMap<>();
                out.put("id", randomLong(1, 1_000_000_000L) + "");
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

    public Object generateRandomValue(DataTypeNodeDTO structure) {
        if (structure.getOptions() != null && !structure.getOptions().isEmpty()) {
            return structure.getOptions().get(random.nextInt(structure.getOptions().size()));
        }

        Map<String, String> validations = validationsToMap(structure.getValidations());

        switch (structure.getType()) {
            case INT: {
                int min = parseIntOrDefault(validations.get("min"), 0);
                int max = parseIntOrDefault(validations.get("max"), 100);
                return randomInt(min, max);
            }
            case FLOAT: {
                double min = parseDoubleOrDefault(validations.get("min"), 0.0);
                double max = parseDoubleOrDefault(validations.get("max"), 100.0);
                return randomDouble(min, max);
            }
            case BOOLEAN:
                return random.nextBoolean();
            case STRING: {
                int minLen = parseIntOrDefault(validations.get("minlength"), 1);
                int maxLen = parseIntOrDefault(
                        validations.get("maxlength"),
                        Math.max(minLen, minLen + 10)
                );
                return generateRandomString(minLen, maxLen);
            }
            case DATE:
                return generateRandomDate();
            case DATE_TIME:
                return generateRandomDateTime();
            case CATEGORICAL:
                return generateRandomString(3, 8);
            case FILE:
                return generateRandomFileName();
            default:
                throw new IllegalArgumentException("Unsupported data type: " + structure.getType());
        }
    }

    private Map<String, String> validationsToMap(List<DataTypeValidationDTO> validations) {
        if (validations == null) {
            return Map.of();
        }
        Map<String, String> map = new HashMap<>();
        for (DataTypeValidationDTO v : validations) {
            if (v.getName() != null && v.getValidator() != null) {
                map.put(v.getName().name().toLowerCase(), v.getValidator());
            }
        }
        return map;
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

    private String generateRandomString(int minLength, int maxLength) {
        String letters = "abcdefghijklmnopqrstuvwxyz";
        int length = randomInt(minLength, maxLength);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(letters.charAt(random.nextInt(letters.length())));
        }
        return sb.toString();
    }


    private String generateRandomDate() {
        int year = randomInt(1970, 2023);
        int month = randomInt(1, 12);
        int day = randomInt(1, 28);
        LocalDate date = LocalDate.of(year, month, day);
        return DATE_FMT.format(date);
    }

    private String generateRandomDateTime() {
        int year = randomInt(1970, 2023);
        int month = randomInt(1, 12);
        int day = randomInt(1, 28);
        int hour = randomInt(0, 23);
        int minute = randomInt(0, 59);
        int second = randomInt(0, 59);
        LocalDateTime dt = LocalDateTime.of(year, month, day, hour, minute, second);
        return DATE_TIME_FMT.format(dt);
    }

    private String generateRandomInstantIso() {
        return generateRandomDateTime() + "Z";
    }

    private String generateRandomFileName() {
        return generateRandomString(8, 8) + ".txt";
    }

    private int randomInt(int min, int max) {
        if (min >= max) {
            return min;
        }
        return min + random.nextInt((max - min) + 1);
    }

    private long randomLong(long min, long max) {
        if (min >= max) {
            return min;
        }
        return min + (Math.abs(random.nextLong()) % (max - min + 1));
    }

    private double randomDouble(double min, double max) {
        if (min >= max) {
            return min;
        }
        return min + (max - min) * random.nextDouble();
    }

    private int parseIntOrDefault(String v, int def) {
        if (v == null) return def;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private double parseDoubleOrDefault(String v, double def) {
        if (v == null) return def;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return def;
        }
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
