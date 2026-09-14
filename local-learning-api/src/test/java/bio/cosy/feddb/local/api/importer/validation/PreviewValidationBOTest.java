package bio.cosy.feddb.local.api.importer.validation;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.statistics.TransformedStatisticsBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
class PreviewValidationBOTest {

    @Test
    void requestRequiresCohortAndInputConfiguration() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var messages = factory.getValidator().validate(new PreviewValidationRequestDTO()).stream()
                    .map(violation -> violation.getMessage())
                    .collect(java.util.stream.Collectors.toSet());

            assertEquals(java.util.Set.of(
                    "Cohort ID is required",
                    "Input configuration is required"
            ), messages);
        }
    }

    @Test
    void validatesDistinctAndMissingValuesAndRetainsUnmappedColumns() {
        TransformedStatisticsBO statisticsBO = new StubTransformedStatisticsBO(List.of(
                profile("age", 1, "17", "42"),
                profile("note", 0, "ok")
        ));
        ConnectorValidationBO validationBO = new StubConnectorValidationBO();
        PreviewValidationBO bo = new PreviewValidationBO();
        bo.transformedStatisticsBO = statisticsBO;
        bo.validationBO = validationBO;
        bo.maxCategoricalValues = 50;

        ConnectorMappingDTO mapping = new ConnectorMappingDTO();
        mapping.setColumn("age");
        mapping.setMapping("Demographics.Age");
        PreviewValidationRequestElementDTO element = new PreviewValidationRequestElementDTO();
        element.setMapping("Demographics.Age");
        element.setSchemaId(23L);
        PreviewValidationRequestDTO request = new PreviewValidationRequestDTO();
        request.setCohortId(5L);
        request.setInputConfig(new FileUploadSettingsDTO());
        request.setSchemaMapping(List.of(mapping));
        request.setElements(List.of(element));

        List<PreviewValidationResponseDTO> events = bo.validate(request)
                .collect().asList().await().indefinitely();
        List<PreviewValidationResponseDTO> result = events.subList(2, 4);

        assertEquals(List.of("age", "note"), events.subList(0, 2).stream()
                .map(PreviewValidationResponseDTO::getColumn)
                .toList());
        assertTrue(events.subList(0, 2).stream().allMatch(event -> event.getChecks().isEmpty()));
        assertEquals(List.of("age", "note"), result.stream().map(PreviewValidationResponseDTO::getColumn).toList());
        // Missingness is checked once against the target's nullability, not treated as a category.
        assertEquals(List.of("17", "42", ""), result.getFirst().getChecks().stream()
                .map(PreviewValidationResponseElementDTO::getValue).toList());
        assertTrue(result.getFirst().getChecks().stream().allMatch(PreviewValidationResponseElementDTO::isMapped));
        assertTrue(result.getFirst().getChecks().stream().allMatch(PreviewValidationResponseElementDTO::isValidated));
        assertFalse(result.getFirst().getChecks().get(1).getResult().isValid());
        assertEquals(23L, result.getFirst().getChecks().getFirst().getResult().getSchemaId());
        assertFalse(result.get(1).getChecks().getFirst().isMapped());
        assertFalse(result.get(1).getChecks().getFirst().isValidated());
        assertNull(result.get(1).getChecks().getFirst().getResult());
    }

    @Test
    void streamsUntouchedColumnsBeforeDeferredTransformedColumns() {
        TransformedStatisticsBO statisticsBO = new StubTransformedStatisticsBO(
                List.of(profile("age", 0, "17"), profile("note", 0, "ok")),
                List.of(profile("age", 0, "adult"), profile("note", 0, "ok"))
        );
        PreviewValidationBO bo = new PreviewValidationBO();
        bo.transformedStatisticsBO = statisticsBO;
        bo.validationBO = new StubConnectorValidationBO();
        bo.maxCategoricalValues = 50;

        ConnectorMappingDTO ageMapping = new ConnectorMappingDTO();
        ageMapping.setColumn("age");
        ageMapping.setMapping("Demographics.Age");
        ConnectorMappingDTO noteMapping = new ConnectorMappingDTO();
        noteMapping.setColumn("note");
        noteMapping.setMapping("Clinical.Note");
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        transformer.setColumn("age");

        PreviewValidationRequestDTO request = new PreviewValidationRequestDTO();
        request.setCohortId(5L);
        request.setInputConfig(new FileUploadSettingsDTO());
        request.setSchemaMapping(List.of(ageMapping, noteMapping));
        request.setTransformer(List.of(transformer));

        List<PreviewValidationResponseDTO> events = bo.validate(request)
                .collect().asList().await().indefinitely();

        assertEquals(4, events.size());
        assertTrue(events.subList(0, 2).stream().allMatch(event -> event.getChecks().isEmpty()));
        assertEquals(List.of("note", "age"), events.subList(2, 4).stream()
                .map(PreviewValidationResponseDTO::getColumn)
                .toList());
        assertEquals("adult", events.get(3).getChecks().getFirst().getValue());
    }

    private ColumnProfile profile(String name, long missing, String... values) {
        List<java.util.Map.Entry<String, Integer>> counts = java.util.Arrays.stream(values)
                .map(value -> (java.util.Map.Entry<String, Integer>)
                        new AbstractMap.SimpleImmutableEntry<>(value, 1))
                .toList();
        return new ColumnProfile(
                name,
                "TEXT",
                values.length + missing,
                missing,
                values.length,
                null, null, null, null, null, null, null,
                counts,
                counts
        );
    }

    private static class StubTransformedStatisticsBO extends TransformedStatisticsBO {
        private final List<ColumnProfile> sourceProfiles;
        private final List<ColumnProfile> transformedProfiles;

        private StubTransformedStatisticsBO(List<ColumnProfile> profiles) {
            this(profiles, profiles);
        }

        private StubTransformedStatisticsBO(
                List<ColumnProfile> sourceProfiles,
                List<ColumnProfile> transformedProfiles
        ) {
            this.sourceProfiles = sourceProfiles;
            this.transformedProfiles = transformedProfiles;
        }

        @Override
        public List<ColumnProfile> getSourceStatistics(
                Long cohortId,
                bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO inputConfig,
                Map<String, bio.cosy.feddb.local.api.importer.extract.UploadInfoDTO> uploadInfo
        ) {
            return sourceProfiles;
        }

        @Override
        public List<ColumnProfile> getTransformedStatistics(
                Long cohortId,
                bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO inputConfig,
                bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO mergeConfig,
                bio.cosy.feddb.local.api.importer.extract.PivotConfigDTO pivotConfig,
                List<ConnectorTransformerDTO> transformers,
                List<ConnectorMappingDTO> schemaMapping
        ) {
            return transformedProfiles;
        }

        @Override
        public List<ColumnProfile> getTransformedStatistics(
                Long cohortId,
                bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO inputConfig,
                Map<String, bio.cosy.feddb.local.api.importer.extract.UploadInfoDTO> uploadInfo,
                bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO mergeConfig,
                bio.cosy.feddb.local.api.importer.extract.PivotConfigDTO pivotConfig,
                List<ConnectorTransformerDTO> transformers,
                List<ConnectorMappingDTO> schemaMapping
        ) {
            return transformedProfiles;
        }
    }

    private static class StubConnectorValidationBO extends ConnectorValidationBO {
        @Override
        public ConnectorValidationResultDTO checkValidations(
                Long cohortId,
                String value,
                Long schemaId,
                String mapping,
                String keycloakId
        ) {
            return new ConnectorValidationResultDTO(
                    value.equals("42") ? "Too large" : "Valid",
                    schemaId,
                    !value.equals("42")
            );
        }
    }
}
