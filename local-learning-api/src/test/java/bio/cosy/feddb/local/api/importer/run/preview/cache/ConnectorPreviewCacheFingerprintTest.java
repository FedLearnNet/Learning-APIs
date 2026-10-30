package bio.cosy.feddb.local.api.importer.run.preview.cache;

import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;


class ConnectorPreviewCacheFingerprintTest {

    private final ConnectorPreviewTransformationCacheBO cache = new ConnectorPreviewTransformationCacheBO();

    private static ConnectorConfigDTO config(Long fileId, Long cohortId) {
        ConnectorConfigDTO config = new ConnectorConfigDTO();
        config.setCohortId(cohortId);
        FileUploadSettingsDTO input = new FileUploadSettingsDTO();
        input.setFileId(fileId);
        config.setInputConfig(input);
        return config;
    }

    private static ConnectorTransformerDTO transformer(String method, Map<String, Object> inputMapping) {
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        transformer.setModuleName("builtin");
        transformer.setMethodName(method);
        transformer.setInputMapping(inputMapping);
        return transformer;
    }

    @Test
    void theSameSourceConfigurationFingerprintsTheSame() {
        assertEquals(cache.sourceFingerprint(config(1L, 7L)), cache.sourceFingerprint(config(1L, 7L)));
    }

    @Test
    void aDifferentSourceFileIsADifferentSample() {
        assertNotEquals(cache.sourceFingerprint(config(1L, 7L)), cache.sourceFingerprint(config(2L, 7L)));
    }

    @Test
    void aDifferentCohortIsADifferentSample() {
        assertNotEquals(cache.sourceFingerprint(config(1L, 7L)), cache.sourceFingerprint(config(1L, 8L)));
    }

    @Test
    void editingATransformerChangesItsOwnStage() {
        String source = cache.sourceFingerprint(config(1L, 7L));
        ConnectorTransformerDTO before = transformer("Replace Value", Map.of("search", "?"));
        ConnectorTransformerDTO after = transformer("Replace Value", Map.of("search", "N/A"));

        assertNotEquals(cache.stageFingerprint(source, before), cache.stageFingerprint(source, after));
    }

    @Test
    void editingAnEarlierStepChangesEveryStageAfterIt() {
        ConnectorTransformerDTO first = transformer("Replace Value", Map.of("search", "?"));
        ConnectorTransformerDTO edited = transformer("Replace Value", Map.of("search", "N/A"));
        ConnectorTransformerDTO second = transformer("Trim", Map.of("column", "race"));

        String source = cache.sourceFingerprint(config(1L, 7L));
        String unchangedChain = cache.stageFingerprint(cache.stageFingerprint(source, first), second);
        String editedChain = cache.stageFingerprint(cache.stageFingerprint(source, edited), second);

        assertNotEquals(unchangedChain, editedChain,
                "A stage is computed from the one before it, so an earlier edit must change it too");
    }

    @Test
    void changingTheSourceChangesTheWholeChain() {
        ConnectorTransformerDTO only = transformer("Replace Value", Map.of("search", "?"));
        assertNotEquals(
                cache.stageFingerprint(cache.sourceFingerprint(config(1L, 7L)), only),
                cache.stageFingerprint(cache.sourceFingerprint(config(2L, 7L)), only));
    }

    @Test
    void theSameSampleFingerprintsTheSameAndADifferentOneDoesNot() {
        List<String> columns = List.of("id", "race");
        List<Map<String, Object>> rows = List.of(Map.of("id", "1", "race", "?"));

        assertEquals(cache.sampleFingerprint(rows, columns), cache.sampleFingerprint(List.copyOf(rows), columns));
        assertNotEquals(cache.sampleFingerprint(rows, columns),
                cache.sampleFingerprint(List.of(Map.of("id", "1", "race", "Other")), columns));
    }

    @Test
    void savedTransformerKeysLikeTheOneTheWizardSends() {
        ConnectorTransformerDTO fromWizard = transformer("Replace Value", Map.of("search", "?"));
        ConnectorTransformerDTO saved = transformer("Replace Value", Map.of("search", "?"));
        saved.setId(12L);
        saved.setPosition(3);
        saved.setConnectorId(4L);
        saved.setCreatedAt(new Date());
        saved.setUpdatedAt(new Date());

        String source = cache.sourceFingerprint(config(1L, 7L));
        assertEquals(cache.stageFingerprint(source, fromWizard), cache.stageFingerprint(source, saved),
                "A run keys stages from the saved connector, so ids and timestamps must not take part");
    }

    @Test
    void keyOrderInTheConfigurationDoesNotChangeTheFingerprint() {
        Map<String, Object> oneOrder = new LinkedHashMap<>();
        oneOrder.put("search", "?");
        oneOrder.put("replace", "null");
        Map<String, Object> otherOrder = new LinkedHashMap<>();
        otherOrder.put("replace", "null");
        otherOrder.put("search", "?");

        String source = cache.sourceFingerprint(config(1L, 7L));
        assertEquals(
                cache.stageFingerprint(source, transformer("Replace Value", oneOrder)),
                cache.stageFingerprint(source, transformer("Replace Value", otherOrder)),
                "Serialisation order must not decide whether the cache hits");
    }
}
