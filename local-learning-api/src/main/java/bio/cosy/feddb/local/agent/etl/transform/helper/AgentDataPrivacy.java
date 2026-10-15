package bio.cosy.feddb.local.agent.etl.transform.helper;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeValueGenerator;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.config.TransformerAgentConfig;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.privacy.PrivacyStatisticsBO;
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
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@ApplicationScoped
public class AgentDataPrivacy {

    private static final Set<String> NUMBER_TYPES = Set.of("INTEGER", "NUMBER");
    private static final int MAX_DAYS_SHIFT = 365;
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd.MM.yyyy"), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    @Inject
    TransformerAgentConfig config;

    @Inject
    PrivacyStatisticsBO privacyStatisticsBO;

    public TableSample sample(TableSample real, Map<String, ColumnProfile> realProfiles) {
        if (!isSynthetic()) {
            return real;
        }
        Map<String, ColumnProfile> profiles = profiles(realProfiles);
        List<Map<String, Object>> rows = real.getRows().stream()
                .filter(Objects::nonNull)
                .map(row -> {
                    Map<String, Object> synthetic = new LinkedHashMap<>();
                    row.forEach((column, value) -> synthetic.put(column, randomValue(profiles.get(column), value)));
                    return synthetic;
                })
                .toList();
        return new TableSample(real.getColumns(), rows, List.copyOf(profiles.values()));
    }

    public Map<String, ColumnProfile> profiles(Map<String, ColumnProfile> real) {
        Map<String, ColumnProfile> profiles = real;
        if (config.useStatisticPrivacy()) {
            profiles = map(profiles, privacyStatisticsBO::sanitizeProfile);
        }
        if (isSynthetic()) {
            profiles = map(profiles, this::randomProfile);
        }
        return profiles;
    }

    public Map<Object, String> rejected(Map<Object, String> real, Map<String, ColumnProfile> profiles) {
        if (!isSynthetic()) {
            return real;
        }
        Set<String> categories = profiles.values().stream()
                .flatMap(profile -> categories(profile).stream())
                .collect(Collectors.toSet());
        Map<Object, String> synthetic = new LinkedHashMap<>();
        real.forEach((value, reason) -> {
            Object random = categories.contains(String.valueOf(value)) ? value : randomValue(null, value);
            synthetic.putIfAbsent(random, reason.replace(String.valueOf(value), String.valueOf(random)));
        });
        return synthetic;
    }

    private boolean isSynthetic() {
        return config.syntheticData().enabled();
    }

    private Map<String, ColumnProfile> map(Map<String, ColumnProfile> profiles, UnaryOperator<ColumnProfile> change) {
        return profiles.values().stream().collect(Collectors.toMap(ColumnProfile::name, change, (a, b) -> a,
                LinkedHashMap::new));
    }

    private ColumnProfile randomProfile(ColumnProfile real) {
        if (!categories(real).isEmpty()) {
            return real;
        }
        return new ColumnProfile(real.name(), real.type(), real.count(), real.missing(), real.uniqueValues(), real.mean(),
                real.std(), real.min(), real.p25(), real.median(), real.p75(), real.max(),
                randomCounts(real, real.topCategories()), randomCounts(real, real.valueCounts()), real.missingValues());
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
        if (value == null || String.valueOf(value).isBlank() || profile != null && categories(profile).contains(String.valueOf(value))) {
            return value;
        }
        String text = String.valueOf(value).strip();
        Random random = new Random(text.hashCode());
        return randomNumber(profile, text, random)
                .or(() -> shiftedDate(text, random))
                .orElseGet(() -> DataTypeValueGenerator.randomLike(text, random));
    }

    private Set<String> categories(ColumnProfile profile) {
        List<Map.Entry<String, Integer>> counts = TransformerHelper.valueCounts(profile);
        return counts.isEmpty() || profile.uniqueValues() > config.syntheticData().categoryLimit() ? Set.of()
                : counts.stream().map(Map.Entry::getKey).collect(Collectors.toSet());
    }

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
