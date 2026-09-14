package bio.cosy.feddb.local.api.importer.run.preview.cache;

import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
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
