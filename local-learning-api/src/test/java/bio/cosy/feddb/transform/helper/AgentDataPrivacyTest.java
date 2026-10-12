package bio.cosy.feddb.transform.helper;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.config.TransformerAgentConfig;
import bio.cosy.feddb.local.agent.etl.transform.helper.AgentDataPrivacy;
import bio.cosy.feddb.local.api.privacy.PrivacyStatisticsBO;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDataPrivacyTest {

    private static final ColumnProfile GENDER = profile("gender", "TEXT", 2, null, null, Map.entry("Female", 60), Map.entry("Male", 40));
    private static final ColumnProfile AGE = profile("age", "INTEGER", 70, 18.0, 90.0, Map.entry("47", 1));
    private static final ColumnProfile PATIENT = profile("patient", "TEXT", 100, null, null, Map.entry("AB-12", 1));
    private static final ColumnProfile ADMITTED = profile("admitted", "DATETIME", 100, null, null, Map.entry("2021-03-04", 1));
    private static final ColumnProfile DRUG = profile("drug", "TEXT", 3, null, null, Map.entry("Insulin", 60),
            Map.entry("None", 38), Map.entry("Rare", 2));
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
        AgentDataPrivacy synthetic = synthetic(true);

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

    @Test
    void appliesThePrivacyOfTheStatistics() {
        Map<String, ColumnProfile> visible = dataPrivacy(false, true).profiles(Map.of("drug", DRUG, "age", AGE));

        assertEquals(List.of(Map.entry("Insulin", 60), Map.entry("None", 40)), visible.get("drug").topCategories());
        assertNull(visible.get("age").max());
    }

    @Test
    void hidesRareCategoriesInTheSyntheticRows() {
        TableSample rows = new TableSample(List.of("drug"), List.of(Map.of("drug", "Insulin"), Map.of("drug", "Rare")), null);

        List<Map<String, Object>> visible = dataPrivacy(true, true).sample(rows, Map.of("drug", DRUG)).getRows();

        assertEquals("Insulin", visible.get(0).get("drug"));
        assertNotEquals("Rare", visible.get(1).get("drug"));
    }

    private static TableSample sample() {
        return new TableSample(List.of("gender", "age", "patient", "admitted"),
                List.of(Map.of("gender", "Female", "age", "47", "patient", "AB-12", "admitted", "2021-03-04")), null);
    }

    private static AgentDataPrivacy synthetic(boolean enabled) {
        return dataPrivacy(enabled, false);
    }

    private static AgentDataPrivacy dataPrivacy(boolean synthetic, boolean statisticPrivacy) {
        TransformerAgentConfig config = mock(TransformerAgentConfig.class, Answers.RETURNS_DEEP_STUBS);
        when(config.syntheticData().enabled()).thenReturn(synthetic);
        when(config.syntheticData().categoryLimit()).thenReturn(20);
        when(config.useStatisticPrivacy()).thenReturn(statisticPrivacy);
        AgentDataPrivacy dataPrivacy = new AgentDataPrivacy();
        set(dataPrivacy, "config", config);
        // Without its config the privacy of the statistics uses its defaults
        set(dataPrivacy, "privacyStatisticsBO", new PrivacyStatisticsBO());
        return dataPrivacy;
    }

    private static void set(AgentDataPrivacy dataPrivacy, String name, Object value) {
        try {
            Field field = AgentDataPrivacy.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(dataPrivacy, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SafeVarargs
    private static ColumnProfile profile(String name, String type, int unique, Double min, Double max,
                                         Map.Entry<String, Integer>... counts) {
        return new ColumnProfile(name, type, 100, 0, unique, null, null, min, null, null, null, max, List.of(counts),
                List.of(counts));
    }
}
