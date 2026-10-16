package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeValueGenerator;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.datamodler.validation.DataTypeValidationDTO;
import bio.cosy.feddb.core.api.datamodler.validation.DataTypeValidationType;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerHelper;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeDTO;
import org.apache.commons.lang3.CharUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HarmonizationProfileHelper {

    public static final String EMPTY = "(empty)";
    private static final Set<String> NUMBER_TYPES = Set.of("INTEGER", "NUMBER");
    private static final int CATEGORY_LIMIT = 20;
    private static final int EXAMPLES = 5;
    private static final int MAX_SHAPES = 10;
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern SHAPE_PART = Pattern.compile("[A-Za-z]+|\\p{Nd}+|\\s+|.", Pattern.DOTALL);

    public static DataTypeDTO dataType(ColumnProfile profile) {
        List<Map.Entry<String, Integer>> values = TransformerHelper.valueCounts(profile);
        // A column of mostly numbers is text when some values are no numbers, like the ICD codes V27 or E888
        String type = values.stream().anyMatch(value -> !NUMBER.matcher(value.getKey()).matches())
                && NUMBER_TYPES.contains(profile.type()) ? "TEXT" : Objects.requireNonNullElse(profile.type(), "TEXT");
        boolean categorical = (type.equals("TEXT") || type.equals("BOOLEAN"))
                && !values.isEmpty() && profile.uniqueValues() <= CATEGORY_LIMIT;
        long empty = profile.missing();
        if (categorical) {
            List<Map.Entry<String, Integer>> markers = TransformerHelper.missingValues(profile).stream()
                    .map(marker -> Map.entry(marker.getKey().strip(), marker.getValue()))
                    .toList();
            values = Stream.concat(values.stream(), markers.stream()).toList();
            empty -= markers.stream().mapToLong(Map.Entry::getValue).sum();
        }
        DataTypeDTO dataType = new DataTypeDTO();
        dataType.setName(profile.name());
        dataType.setDescription(describe(profile, values, categorical ? "CATEGORICAL" : type, empty));
        dataType.setType(categorical ? DataTypes.CATEGORICAL : switch (type) {
            case "INTEGER" -> DataTypes.INT;
            case "NUMBER" -> DataTypes.FLOAT;
            case "DATETIME" -> DataTypes.DATE_TIME;
            default -> DataTypes.STRING;
        });
        if (categorical) {
            dataType.setAllowedValues(values.stream().map(Map.Entry::getKey).collect(Collectors.toCollection(LinkedHashSet::new)));
        } else if (dataType.getType() == DataTypes.STRING && profile.valueCounts() != null) {
            pattern(profile.valueCounts()).ifPresent(pattern -> dataType.setValidations(Set.of(pattern)));
        }
        return dataType;
    }


    static Optional<DataTypeValidationDTO> pattern(List<Map.Entry<String, Integer>> values) {
        Set<String> shapes = values.stream()
                .map(value -> shape(value.getKey()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (shapes.isEmpty() || shapes.size() > MAX_SHAPES) {
            return Optional.empty();
        }
        DataTypeValidationDTO pattern = new DataTypeValidationDTO();
        pattern.setName(DataTypeValidationType.PATTERN);
        pattern.setValidator("(?:" + String.join("|", shapes) + ")");
        return Optional.of(pattern);
    }

    public static Object sampleValue(ColumnProfile profile, Random random) {
        if (profile.count() > 0 && random.nextDouble() < (double) profile.missing() / profile.count()) {
            return null;
        }
        List<Map.Entry<String, Integer>> values = TransformerHelper.valueCounts(profile);
        boolean number = NUMBER_TYPES.contains(profile.type()) && profile.min() != null && profile.max() != null;
        if (number && (values.isEmpty() || values.size() > CATEGORY_LIMIT)) {
            double value = DataTypeValueGenerator.randomDouble(profile.min(), profile.max(), random);
            return "INTEGER".equals(profile.type()) ? (Object) Math.round(value) : number(value);
        }
        if (values.isEmpty()) {
            return null;
        }
        int pick = random.nextInt(values.stream().mapToInt(Map.Entry::getValue).sum());
        for (Map.Entry<String, Integer> value : values) {
            pick -= value.getValue();
            if (pick < 0) {
                return value.getKey();
            }
        }
        return values.getLast().getKey();
    }

    private static String shape(String value) {
        return SHAPE_PART.matcher(value).results().map(MatchResult::group).map(part -> {
            char first = part.charAt(0);
            return CharUtils.isAsciiAlpha(first) ? "[A-Za-z]+" : Character.isDigit(first) ? "\\d+"
                    : Character.isWhitespace(first) ? "\\s+" : Character.isLetter(first) ? part : "\\" + part;
        }).collect(Collectors.joining());
    }


    private static String describe(ColumnProfile profile, List<Map.Entry<String, Integer>> values, String type, long empty) {
        String missing = profile.count() == 0 || empty == 0 ? ""
                : " " + percent(empty, profile.count()) + " of the values are missing.";
        if (NUMBER_TYPES.contains(type) && profile.min() != null && profile.max() != null) {
            // Only what the reference holds, a file harmonized onto it can hold other numbers of the same meaning
            return "Numbers in the reference file range from " + number(profile.min()) + " to " + number(profile.max())
                    + " (observed, not a limit), median " + number(profile.median()) + ", mean " + number(profile.mean())
                    + "." + missing;
        }
        if (values.isEmpty() || profile.count() == 0) {
            return missing.strip();
        }
        boolean categorical = type.equals("CATEGORICAL");
        String shares = Stream.concat(values.stream()
                                .limit(categorical ? values.size() : EXAMPLES)
                                .map(value -> value.getKey() + " " + percent(value.getValue(), profile.count())),
                        Stream.of(EMPTY + " " + percent(empty, profile.count())).filter(share -> empty > 0))
                .collect(Collectors.joining(", "));
        return (categorical ? "Values of the reference file with their share of all rows: " : "Most frequent values of the "
                + "reference file with their share of all rows (examples, other values of the same kind are allowed): ") + shares + ".";
    }

    private static String percent(long part, long total) {
        return Math.round(100.0 * part / total) + "%";
    }

    private static String number(Double value) {
        return value == null ? "?" : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
