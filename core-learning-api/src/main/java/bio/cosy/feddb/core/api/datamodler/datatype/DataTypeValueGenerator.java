package bio.cosy.feddb.core.api.datamodler.datatype;

import bio.cosy.feddb.core.api.datamodler.validation.DataTypeValidationDTO;
import bio.cosy.feddb.core.api.datamodler.validation.DataTypeValidationType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;


public class DataTypeValueGenerator {

    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz";
    private static final int MIN_YEAR = 1970;
    private static final int MAX_YEAR = 2023;

    private DataTypeValueGenerator() {
    }

    public static Object randomValue(DataTypeNodeDTO dataType, Random random) {
        if (dataType.getOptions() != null && !dataType.getOptions().isEmpty()) {
            return dataType.getOptions().get(random.nextInt(dataType.getOptions().size()));
        }
        return switch (dataType.getType()) {
            case INT -> randomInt((int) validation(dataType, DataTypeValidationType.MIN, 0),
                    (int) validation(dataType, DataTypeValidationType.MAX, 100), random);
            case FLOAT -> randomDouble(validation(dataType, DataTypeValidationType.MIN, 0),
                    validation(dataType, DataTypeValidationType.MAX, 100), random);
            case BOOLEAN -> random.nextBoolean();
            case STRING -> {
                int minLength = (int) validation(dataType, DataTypeValidationType.MINLENGTH, 1);
                yield randomString(minLength, (int) validation(dataType, DataTypeValidationType.MAXLENGTH, minLength + 10), random);
            }
            case DATE -> DateTimeFormatter.ISO_LOCAL_DATE.format(randomDate(random));
            case DATE_TIME -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(randomDateTime(random));
            case CATEGORICAL -> randomString(3, 8, random);
            case FILE -> randomString(8, 8, random) + ".txt";
        };
    }

    public static String randomLike(String value, Random random) {
        StringBuilder like = new StringBuilder(value.length());
        value.chars().forEach(c -> like.append(Character.isUpperCase(c) ? (char) ('A' + random.nextInt(26))
                : Character.isLowerCase(c) ? (char) ('a' + random.nextInt(26))
                : Character.isDigit(c) ? (char) ('0' + random.nextInt(10)) : (char) c));
        return like.toString();
    }

    public static int randomInt(int min, int max, Random random) {
        return min >= max ? min : min + random.nextInt(max - min + 1);
    }

    public static long randomLong(long min, long max, Random random) {
        return min >= max ? min : min + Math.floorMod(random.nextLong(), max - min + 1);
    }

    public static double randomDouble(double min, double max, Random random) {
        return min >= max ? min : min + (max - min) * random.nextDouble();
    }

    public static String randomString(int minLength, int maxLength, Random random) {
        StringBuilder text = new StringBuilder();
        for (int index = randomInt(minLength, maxLength, random); index > 0; index--) {
            text.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        }
        return text.toString();
    }

    public static LocalDate randomDate(Random random) {
        return LocalDate.of(randomInt(MIN_YEAR, MAX_YEAR, random), randomInt(1, 12, random), randomInt(1, 28, random));
    }

    public static LocalDateTime randomDateTime(Random random) {
        return randomDate(random).atTime(randomInt(0, 23, random), randomInt(0, 59, random), randomInt(0, 59, random));
    }

    private static double validation(DataTypeNodeDTO dataType, DataTypeValidationType type, double defaultValue) {
        if (dataType.getValidations() == null) {
            return defaultValue;
        }
        return dataType.getValidations().stream()
                .filter(validation -> validation.getName() == type && validation.getValidator() != null)
                .map(DataTypeValidationDTO::getValidator)
                .findFirst()
                .map(validator -> {
                    try {
                        return Double.parseDouble(validator);
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }
}
