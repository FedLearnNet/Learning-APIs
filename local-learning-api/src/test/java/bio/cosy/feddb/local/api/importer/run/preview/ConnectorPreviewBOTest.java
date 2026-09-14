package bio.cosy.feddb.local.api.importer.run.preview;

import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.ConnectorExtractBO;
import bio.cosy.feddb.local.api.importer.files.read.TabularFileReaderBO;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import bio.cosy.feddb.local.api.importer.functions.FunctionRunnerBO;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.FilterPatientByHitFunction;
import bio.cosy.feddb.local.api.importer.mapping.MappingBO;
import bio.cosy.feddb.local.api.importer.run.preview.cache.ConnectorPreviewTransformationCacheBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectorPreviewBOTest {

    @Test
    void previewFiltersCompletePatientGroupsUsingTheMappedPatientIdColumn() {
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        ConnectorConfigDTO config = new ConnectorConfigDTO();
        config.setTransformer(List.of(transformer));

        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row("patient-1", "AfricanAmerican"),
                row("patient-2", "Hispanic"),
                row("patient-1", "Caucasian"),
                row("patient-3", "Caucasian")
        ));
        TableData data = new TableData(
                List.of("patient_nbr", "race"),
                rows,
                new ArrayList<>()
        );

        ConnectorExtractBO extractBO = mock(ConnectorExtractBO.class);
        TabularFileReaderBO fileHandlerBO = mock(TabularFileReaderBO.class);
        ConnectorTransformerBO transformerBO = mock(ConnectorTransformerBO.class);
        FunctionRunnerBO functionRunnerBO = mock(FunctionRunnerBO.class);
        MappingBO mappingBO = mock(MappingBO.class);
        FilterPatientByHitFunction filter = new FilterPatientByHitFunction();
        List<List<String>> transformedPatientIds = new ArrayList<>();

        when(extractBO.loadPreviewData(isNull(), isNull(), anyInt(), isNull(), isNull()))
                .thenReturn(data);
        when(mappingBO.resolveExternalIdSourceColumn(isNull())).thenReturn("patient_nbr");
        when(functionRunnerBO.mode(same(transformer))).thenReturn(FunctionExecutionMode.PATIENT);
        // Patient grouping now lives in ConnectorTransformerBO#applyBuiltInTransformation (tested
        // there); here we reproduce it so the preview's json output can still be asserted.
        when(transformerBO.applyBuiltInTransformation(same(transformer), anyList(), eq("patient_nbr")))
                .thenAnswer(invocation -> {
                    List<Map<String, Object>> allRows = invocation.getArgument(1);
                    Map<String, List<Map<String, Object>>> byPatient = new LinkedHashMap<>();
                    for (Map<String, Object> row : allRows) {
                        byPatient.computeIfAbsent(String.valueOf(row.get("patient_nbr")),
                                ignored -> new ArrayList<>()).add(row);
                    }
                    List<Map<String, Object>> out = new ArrayList<>();
                    for (List<Map<String, Object>> patientRows : byPatient.values()) {
                        transformedPatientIds.add(patientRows.stream()
                                .map(row -> String.valueOf(row.get("patient_nbr")))
                                .toList());
                        out.addAll(filter.apply(
                                patientRows,
                                null,
                                Map.of("value", "race"),
                                Map.of(),
                                Map.of("hit", "Caucasian", "case_sensitive", false),
                                null,
                                null,
                                null
                        ));
                    }
                    return out;
                });
        when(fileHandlerBO.getJson(org.mockito.ArgumentMatchers.any(TableData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, TableData.class).getRows().stream()
                        .map(row -> String.valueOf(row.get("patient_nbr")))
                        .toList()
                        .toString());

        ConnectorPreviewBO previewBO = new ConnectorPreviewBO();
        previewBO.cacheBO = new ConnectorPreviewTransformationCacheBO();
        previewBO.extractBO = extractBO;
        previewBO.fileHandlerBO = fileHandlerBO;
        previewBO.transformerBO = transformerBO;
        previewBO.functionRunnerBO = functionRunnerBO;
        previewBO.mappingBO = mappingBO;

        PreviewResponseDTO response = previewBO.preview(config);

        assertEquals(List.of(
                List.of("patient-1", "patient-1"),
                List.of("patient-2"),
                List.of("patient-3")
        ), transformedPatientIds);
        assertEquals(List.of("[patient-1, patient-1, patient-3]"), response.getJsons());
    }

    @Test
    void patientPreviewSampleScansPastItsPatientLimitToCompleteSelectedGroups() {
        ConnectorPreviewBO previewBO = new ConnectorPreviewBO();
        previewBO.cacheBO = new ConnectorPreviewTransformationCacheBO();
        TableData data = new TableData(
                List.of("patient_nbr", "race"),
                new ArrayList<>(List.of(
                        row("patient-1", "AfricanAmerican"),
                        row("patient-2", "Hispanic"),
                        row("patient-3", "Hispanic"),
                        row("patient-4", "Hispanic"),
                        row("patient-5", "Hispanic"),
                        row("patient-6", "Hispanic"),
                        row("patient-7", "Hispanic"),
                        row("patient-8", "Hispanic"),
                        row("patient-9", "Hispanic"),
                        row("patient-10", "Hispanic"),
                        row("patient-11", "Caucasian"),
                        row("patient-1", "Caucasian")
                )),
                new ArrayList<>()
        );

        List<Map<String, Object>> sample = previewBO.selectPatientPreviewRows(data, "patient_nbr");

        assertEquals(11, sample.size());
        assertEquals(2, sample.stream()
                .filter(row -> "patient-1".equals(row.get("patient_nbr")))
                .count());
        assertEquals(0, sample.stream()
                .filter(row -> "patient-11".equals(row.get("patient_nbr")))
                .count());
    }

    @Test
    void pivotPreviewReturnsExtractedPivotDataWithoutRunningTransformers() {
        ConnectorExtractBO extractBO = mock(ConnectorExtractBO.class);
        TabularFileReaderBO fileHandlerBO = mock(TabularFileReaderBO.class);
        TableData pivoted = new TableData(
                List.of("patient_id", "age"),
                List.of(row("p1", "31")),
                new ArrayList<>()
        );
        when(extractBO.loadPreviewData(isNull(), isNull(), anyInt(), isNull(), isNull()))
                .thenReturn(pivoted);
        // Matched by type, not identity: each stage is rendered through a TableData built for it,
        // so the preview no longer hands the extractor's own instance to the serializer.
        when(fileHandlerBO.getJson(any(TableData.class))).thenReturn("[{\"patient_id\":\"p1\",\"age\":\"31\"}]");

        ConnectorPreviewBO previewBO = new ConnectorPreviewBO();
        previewBO.cacheBO = new ConnectorPreviewTransformationCacheBO();
        previewBO.extractBO = extractBO;
        previewBO.fileHandlerBO = fileHandlerBO;

        PreviewResponseDTO response = previewBO.previewPivot(new ConnectorConfigDTO());

        assertEquals(List.of("[{\"patient_id\":\"p1\",\"age\":\"31\"}]"), response.getJsons());
    }

    @Test
    void appBasedStepIsReportedAsNeedingARunAndKeepsShowingItsInput() {
        ConnectorTransformerDTO appTransformer = new ConnectorTransformerDTO();
        appTransformer.setAppImage("transformer-combine-rows:qfyjTIKg");
        ConnectorTransformerDTO afterIt = new ConnectorTransformerDTO();

        ConnectorConfigDTO config = new ConnectorConfigDTO();
        config.setTransformer(List.of(appTransformer, afterIt));

        TableData data = new TableData(
                List.of("patient_nbr", "race"),
                new ArrayList<>(List.of(row("patient-1", "Caucasian"))),
                new ArrayList<>()
        );

        ConnectorExtractBO extractBO = mock(ConnectorExtractBO.class);
        TabularFileReaderBO fileHandlerBO = mock(TabularFileReaderBO.class);
        ConnectorTransformerBO transformerBO = mock(ConnectorTransformerBO.class);
        FunctionRunnerBO functionRunnerBO = mock(FunctionRunnerBO.class);

        when(extractBO.loadPreviewData(isNull(), isNull(), anyInt(), isNull(), isNull()))
                .thenReturn(data);
        when(transformerBO.applyBuiltInTransformation(same(afterIt), anyList(), isNull()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(fileHandlerBO.getJson(any(TableData.class))).thenReturn("[]");

        ConnectorPreviewBO previewBO = new ConnectorPreviewBO();
        previewBO.cacheBO = new ConnectorPreviewTransformationCacheBO();
        previewBO.extractBO = extractBO;
        previewBO.fileHandlerBO = fileHandlerBO;
        previewBO.transformerBO = transformerBO;
        previewBO.functionRunnerBO = functionRunnerBO;

        PreviewResponseDTO response = previewBO.preview(config);

        // One table and one stage per step, so the wizard can line them up with its cards - the app
        // step used to be skipped outright, which shifted every later step's table onto the wrong card.
        assertEquals(2, response.getJsons().size());
        assertEquals(2, response.getStages().size());

        PreviewStageDTO appStage = response.getStages().get(0);
        assertTrue(appStage.isAppBased());
        assertTrue(appStage.isRequiresRun());
        assertEquals(1, appStage.getRowCount());

        PreviewStageDTO builtInStage = response.getStages().get(1);
        assertFalse(builtInStage.isAppBased());
        assertFalse(builtInStage.isRequiresRun());
        // It was computed from the app step's input rather than its result, so it says which step
        // has to run first - and it is never cached, or it would be served after that step has run.
        assertEquals(1, builtInStage.getBlockedByStep());
    }

    @Test
    void aStepAfterAnUnrunAppStepCannotBePrepared() {
        ConnectorTransformerDTO appTransformer = new ConnectorTransformerDTO();
        appTransformer.setAppImage("transformer-combine-rows:qfyjTIKg");
        ConnectorTransformerDTO secondApp = new ConnectorTransformerDTO();
        secondApp.setAppImage("transformer-split-rows:vElEmkSt");

        ConnectorConfigDTO config = new ConnectorConfigDTO();
        config.setTransformer(List.of(appTransformer, secondApp));

        ConnectorExtractBO extractBO = mock(ConnectorExtractBO.class);
        when(extractBO.loadPreviewData(isNull(), isNull(), anyInt(), isNull(), isNull()))
                .thenReturn(new TableData(
                        List.of("patient_nbr", "race"),
                        new ArrayList<>(List.of(row("patient-1", "Caucasian"))),
                        new ArrayList<>()));

        ConnectorPreviewBO previewBO = new ConnectorPreviewBO();
        previewBO.cacheBO = new ConnectorPreviewTransformationCacheBO();
        previewBO.extractBO = extractBO;

        // Running the second app on the first one's input would silently produce nonsense.
        assertThrows(BadRequestException.class, () -> previewBO.prepareAppStage(config, 2));
    }

    private static Map<String, Object> row(String patientId, String race) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("patient_nbr", patientId);
        row.put("race", race);
        return row;
    }
}
