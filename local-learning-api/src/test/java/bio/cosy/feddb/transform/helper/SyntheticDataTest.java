package bio.cosy.feddb.transform.helper;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.config.TransformerAgentConfig;
import bio.cosy.feddb.local.agent.etl.transform.helper.SyntheticData;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SyntheticDataTest {

    private static final ColumnProfile GENDER = profile("gender", "TEXT", 2, null, null, Map.entry("Female", 60), Map.entry("Male", 40));
    private static final ColumnProfile AGE = profile("age", "INTEGER", 70, 18.0, 90.0, Map.entry("47", 1));
    private static final ColumnProfile PATIENT = profile("patient", "TEXT", 100, null, null, Map.entry("AB-12", 1));
    private static final ColumnProfile ADMITTED = profile("admitted", "DATETIME", 100, null, null, Map.entry("2021-03-04", 1));
    private static final Map<String, ColumnProfile> PROFILES = Map.of("gender", GENDER, "age", AGE, "patient", PATIENT,
            "admitted", ADMITTED);

    @Test
    void keepsTheRealDataWhenDisabled() {
        TableSample real = sample();

        assertSame(real, synthetic(false).sample(real, PROFILES));
        assertSame(PROFILES, synthetic(false).profiles(PROFILES));
    }

    @Test
    void replacesEverythingButCategoriesByRandomValuesOfTheSameKind() {
        Map<String, Object> row = synthetic(true).sample(sample(), PROFILES).getRows().getFirst();

        assertEquals("Female", row.get("gender"));
        long age = Long.parseLong(String.valueOf(row.get("age")));
        assertTrue(age >= 18 && age <= 90, "age " + age);
        String patient = String.valueOf(row.get("patient"));
        assertNotEquals("AB-12", patient);
        assertTrue(patient.matches("[A-Z]{2}-\\d{2}"), patient);
        LocalDate admitted = LocalDate.parse(String.valueOf(row.get("admitted")));
        assertNotEquals(LocalDate.of(2021, 3, 4), admitted);
    }

    @Test
    void givesTheSameValueAlwaysTheSameRandomOne() {
        SyntheticData synthetic = synthetic(true);

        assertEquals(synthetic.sample(sample(), PROFILES).getRows(), synthetic.sample(sample(), PROFILES).getRows());
        assertEquals(synthetic.sample(sample(), PROFILES).getRows().getFirst().get("patient"),
                synthetic.profiles(PROFILES).get("patient").valueCounts().getFirst().getKey());
    }

    @Test
    void hidesRejectedValuesTheAgentDidNotSeeAsCategory() {
        Map<Object, String> rejected = synthetic(true).rejected(Map.of("AB-12", "Value AB-12 is no number", "Male", "no number"),
                PROFILES);

        assertEquals("no number", rejected.get("Male"));
        assertTrue(rejected.keySet().stream().noneMatch("AB-12"::equals));
        assertTrue(rejected.values().stream().noneMatch(reason -> reason.contains("AB-12")));
    }

    private static TableSample sample() {
        return new TableSample(List.of("gender", "age", "patient", "admitted"),
                List.of(Map.of("gender", "Female", "age", "47", "patient", "AB-12", "admitted", "2021-03-04")), null);
    }

    private static SyntheticData synthetic(boolean enabled) {
        TransformerAgentConfig config = mock(TransformerAgentConfig.class, Answers.RETURNS_DEEP_STUBS);
        when(config.syntheticData().enabled()).thenReturn(enabled);
        when(config.syntheticData().categoryLimit()).thenReturn(20);
        SyntheticData synthetic = new SyntheticData();
        try {
            Field field = SyntheticData.class.getDeclaredField("config");
            field.setAccessible(true);
            field.set(synthetic, config);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return synthetic;
    }

    @SafeVarargs
    private static ColumnProfile profile(String name, String type, int unique, Double min, Double max,
                                         Map.Entry<String, Integer>... counts) {
        return new ColumnProfile(name, type, 100, 0, unique, null, null, min, null, null, null, max, List.of(counts),
                List.of(counts));
    }
}
