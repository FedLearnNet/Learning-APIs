package bio.cosy.feddb.local.agent.etl.transform.helper;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeValueGenerator;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.config.TransformerAgentConfig;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Replaces the values of a file by random ones before the LLM sees them, when flnet.connector.agent.synthetic-data is
 * enabled. The statistics and the shape of the values stay, so the agent can still find and convert the columns:
 * <ul>
 *   <li>categories, i.e. columns with few distinct values, keep their values: they are the codebook to map</li>
 *   <li>numbers become a random number in the range of their column</li>
 *   <li>dates move by a random number of days and keep their format</li>
 *   <li>everything else, e.g. ids, names or free text, gets random letters and digits of the same shape: AB-12 -> QX-07</li>
 * </ul>
 * The same value always gets the same random one, so a patient keeps its rows and a column keeps its value counts.
 */
@ApplicationScoped
public class SyntheticData {

    private static final Set<String> NUMBER_TYPES = Set.of("INTEGER", "NUMBER");
    private static final int MAX_DAYS_SHIFT = 365;
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd.MM.yyyy"), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    @Inject
    TransformerAgentConfig config;

    public boolean isEnabled() {
        return config.syntheticData().enabled();
    }

    /** The preview rows with random values, the real ones when synthetic data is off. */
    public TableSample sample(TableSample real, Map<String, ColumnProfile> profiles) {
        if (!isEnabled()) {
            return real;
        }
        List<Map<String, Object>> rows = real.getRows().stream()
                .filter(Objects::nonNull)
                .map(row -> {
                    Map<String, Object> synthetic = new LinkedHashMap<>();
                    row.forEach((column, value) -> synthetic.put(column, randomValue(profiles.get(column), value)));
                    return synthetic;
                })
                .toList();
        return new TableSample(real.getColumns(), rows, List.copyOf(profiles(profiles).values()));
    }

    /** The statistics with random values in place of the real ones, the counts stay. */
    public Map<String, ColumnProfile> profiles(Map<String, ColumnProfile> real) {
        if (!isEnabled()) {
            return real;
        }
        return real.values().stream().collect(Collectors.toMap(ColumnProfile::name, this::profile, (a, b) -> a,
                LinkedHashMap::new));
    }

    /**
     * Real values a validation rejected, with random values in place of the ones the agent did not see as category;
     * the reasons mention the value as well.
     */
    public Map<Object, String> rejected(Map<Object, String> real, Map<String, ColumnProfile> profiles) {
        if (!isEnabled()) {
            return real;
        }
        Set<String> categories = profiles.values().stream()
                .filter(this::isCategory)
                .flatMap(profile -> profile.valueCounts().stream().map(Map.Entry::getKey))
                .collect(Collectors.toSet());
        Map<Object, String> synthetic = new LinkedHashMap<>();
        real.forEach((value, reason) -> {
            Object random = categories.contains(String.valueOf(value)) ? value : randomValue(null, value);
            synthetic.putIfAbsent(random, reason.replace(String.valueOf(value), String.valueOf(random)));
        });
        return synthetic;
    }

    private ColumnProfile profile(ColumnProfile real) {
        if (isCategory(real)) {
            return real;
        }
        return new ColumnProfile(real.name(), real.type(), real.count(), real.missing(), real.uniqueValues(), real.mean(),
                real.std(), real.min(), real.p25(), real.median(), real.p75(), real.max(),
                randomCounts(real, real.topCategories()), randomCounts(real, real.valueCounts()));
    }

    private List<Map.Entry<String, Integer>> randomCounts(ColumnProfile profile, List<Map.Entry<String, Integer>> counts) {
        if (counts == null) {
            return null;
        }
        return counts.stream()
                .collect(Collectors.toMap(entry -> String.valueOf(randomValue(profile, entry.getKey())), Map.Entry::getValue,
                        Integer::sum, LinkedHashMap::new))
                .entrySet().stream().toList();
    }

    private Object randomValue(ColumnProfile profile, Object value) {
        if (value == null || String.valueOf(value).isBlank() || profile != null && isCategory(profile)) {
            return value;
        }
        String text = String.valueOf(value).strip();
        Random random = new Random(text.hashCode());
        return randomNumber(profile, text, random)
                .or(() -> shiftedDate(text, random))
                .orElseGet(() -> DataTypeValueGenerator.randomLike(text, random));
    }

    private boolean isCategory(ColumnProfile profile) {
        return profile.valueCounts() != null && profile.uniqueValues() <= config.syntheticData().categoryLimit();
    }

    // A number in the range of its column, with as many decimals as the real one
    private Optional<Object> randomNumber(ColumnProfile profile, String text, Random random) {
        if (profile == null || !NUMBER_TYPES.contains(profile.type()) || profile.min() == null || profile.max() == null) {
            return Optional.empty();
        }
        try {
            int decimals = Math.max(0, new BigDecimal(text).scale());
            double number = DataTypeValueGenerator.randomDouble(profile.min(), profile.max(), random);
            return Optional.of(BigDecimal.valueOf(number).setScale(decimals, RoundingMode.HALF_UP).toPlainString());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // A date moved by up to a year, in the format of the real one
    private Optional<Object> shiftedDate(String text, Random random) {
        long days = random.nextInt(2 * MAX_DAYS_SHIFT + 1) - MAX_DAYS_SHIFT;
        for (DateTimeFormatter format : DATE_TIME_FORMATS) {
            try {
                return Optional.of(format.format(LocalDateTime.parse(text, format).plusDays(days)));
            } catch (DateTimeParseException e) {
                // Not this format
            }
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return Optional.of(format.format(LocalDate.parse(text, format).plusDays(days)));
            } catch (DateTimeParseException e) {
                // Not this format
            }
        }
        return Optional.empty();
    }
}
