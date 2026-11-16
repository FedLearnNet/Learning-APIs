package bio.cosy.feddb.transform.tools;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.model.TransformerRunContext;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerColumnTool;
import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TransformerColumnToolTest {

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    TransformerColumnTool tool;

    @BeforeEach
    void setUp() {
        Map<String, ColumnProfile> profiles = new HashMap<>();
        profiles.put("rx_a", profile("rx_a", "-", "=", "+"));
        profiles.put("rx_b", profile("rx_b", "-", "="));
        profiles.put("rx_c", profile("rx_c", "-"));
        profiles.put("age", profile("age", "[70-80)", "[60-70)"));
        ConnectorConfigDTO config = new ConnectorConfigDTO();
        ConnectorTransformerDTO mapper = new ConnectorTransformerDTO();
        mapper.setModuleName("builtin");
        mapper.setMethodName("Generic Value Mapper");
        mapper.setColumn("rx_b");
        mapper.setHyperparams(new LinkedHashMap<>(Map.of("map", Map.of("-", "No", "=", "Steady"))));
        config.setTransformer(new ArrayList<>(List.of(mapper)));
        Map<String, String> mapped = new HashMap<>(Map.of("rx_b", "Medication.B", "patient", "Unique Patient ID"));
        TransformerRunContext context = new TransformerRunContext(config, "patient", "keycloak-user", profiles, mapped,
                new ArrayList<>(), null);
        tool.init(context, List.of("patient", "rx_a", "rx_b", "rx_c", "age"));
    }

    @Test
    void listsColumnsWithMostlyTheSameValuesAndHowTheyWereMapped() {
        String related = tool.relatedColumns("rx_a");

        assertTrue(related.contains("- rx_b") && related.contains("mapped onto Medication.B with [Generic Value Mapper")
                && related.contains("\"map\""), related);
        assertFalse(related.contains("rx_c"), "rx_c shares one of three values: " + related);
        assertFalse(related.contains("age"), related);
    }

    @Test
    void saysWhenAColumnIsNotMappedYet() {
        assertTrue(tool.relatedColumns("rx_c").contains("- rx_a") && tool.relatedColumns("rx_c").contains("not mapped yet"),
                tool.relatedColumns("rx_c"));
    }

    @Test
    void givesNothingForAnUnknownColumnOrOneWithoutRelatedColumns() {
        assertEquals("", tool.relatedColumns("rx_x"));
        assertEquals("", tool.relatedColumns("age"));
    }

    private static ColumnProfile profile(String column, String... values) {
        List<Map.Entry<String, Integer>> counts = Arrays.stream(values).<Map.Entry<String, Integer>>map(value -> Map.entry(value, 10)).toList();
        return new ColumnProfile(column, "TEXT", 100, 0, values.length, null, null, null, null, null, null, null, counts, counts);
    }
}
