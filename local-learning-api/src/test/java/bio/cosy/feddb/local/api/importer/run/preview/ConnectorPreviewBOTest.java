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
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformationBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        ConnectorTransformationBO transformationBO = mock(ConnectorTransformationBO.class);
        FunctionRunnerBO functionRunnerBO = mock(FunctionRunnerBO.class);
        MappingBO mappingBO = mock(MappingBO.class);
        FilterPatientByHitFunction filter = new FilterPatientByHitFunction();
        List<List<String>> transformedPatientIds = new ArrayList<>();

        when(extractBO.loadPreviewData(isNull(), isNull(), anyInt(), isNull(), isNull(), isNull()))
                .thenReturn(data);
        when(mappingBO.resolveExternalIdSourceColumn(isNull())).thenReturn("patient_nbr");
        when(functionRunnerBO.mode(same(transformer))).thenReturn(FunctionExecutionMode.PATIENT);
        // Patient grouping now lives in ConnectorTransformationBO#applyBuiltInTransformation (tested
        // there); here we reproduce it so the preview's json output can still be asserted.
        when(transformationBO.applyBuiltInTransformation(same(transformer), anyList(), eq("patient_nbr")))
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
        previewBO.transformationBO = transformationBO;
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
        when(extractBO.loadPreviewData(isNull(), isNull(), anyInt(), isNull(), isNull(), isNull()))
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
    void previewListsTheColumnOrderOfEachTableItReturns() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("patient_id", "p1");
        source.put("2019", 5);
        source.put("sex", "f");
        ConnectorExtractBO extractBO = mock(ConnectorExtractBO.class);
        when(extractBO.loadPreviewData(isNull(), isNull(), anyInt(), isNull(), isNull(), isNull()))
                .thenReturn(new TableData(List.of("patient_id", "2019", "sex"), List.of(source), new ArrayList<>()));

        ConnectorPreviewBO previewBO = new ConnectorPreviewBO();
        previewBO.cacheBO = new ConnectorPreviewTransformationCacheBO();
        previewBO.extractBO = extractBO;
        previewBO.fileHandlerBO = mock(TabularFileReaderBO.class);

        PreviewResponseDTO response = previewBO.previewPivot(new ConnectorConfigDTO());

        // A browser parsing the rows lists number-like keys such as "2019" first, so the order the
        // rows have is sent along with them.
        assertEquals(List.of(List.of("patient_id", "2019", "sex")), response.getColumns());
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
        ConnectorTransformationBO transformationBO = mock(ConnectorTransformationBO.class);
        FunctionRunnerBO functionRunnerBO = mock(FunctionRunnerBO.class);

        when(extractBO.loadPreviewData(isNull(), isNull(), anyInt(), isNull(), isNull(), isNull()))
                .thenReturn(data);
        when(transformationBO.applyBuiltInTransformation(same(afterIt), anyList(), isNull()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(fileHandlerBO.getJson(any(TableData.class))).thenReturn("[]");

        ConnectorPreviewBO previewBO = new ConnectorPreviewBO();
        previewBO.cacheBO = new ConnectorPreviewTransformationCacheBO();
        previewBO.extractBO = extractBO;
        previewBO.fileHandlerBO = fileHandlerBO;
        previewBO.transformationBO = transformationBO;
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
        when(extractBO.loadPreviewData(isNull(), isNull(), anyInt(), isNull(), isNull(), isNull()))
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

    @Test
    void aRunCachesItsAppStepsAndTheStepsAfterThem() {
        ConnectorTransformerDTO appTransformer = new ConnectorTransformerDTO();
        appTransformer.setAppImage("transformer-combine-rows:qfyjTIKg");
        ConnectorTransformerDTO afterIt = new ConnectorTransformerDTO();

        ConnectorConfigDTO config = new ConnectorConfigDTO();
        config.setConnectorId(3L);
        config.setTransformer(List.of(appTransformer, afterIt));

        ConnectorExtractBO extractBO = mock(ConnectorExtractBO.class);
        when(extractBO.loadPreviewData(isNull(), isNull(), anyInt(), isNull(), isNull(), isNull()))
                .thenReturn(new TableData(
                        List.of("patient_nbr", "race"),
                        new ArrayList<>(List.of(row("patient-1", "Caucasian"))),
                        new ArrayList<>()));
        ConnectorTransformationBO transformationBO = mock(ConnectorTransformationBO.class);
        when(transformationBO.applyBuiltInTransformation(same(afterIt), anyList(), isNull()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        ConnectorPreviewTransformationCacheBO cacheBO =
                mock(ConnectorPreviewTransformationCacheBO.class, CALLS_REAL_METHODS);
        doReturn(Optional.empty()).when(cacheBO).find(any(), anyInt(), any());
        doNothing().when(cacheBO).store(any(), anyInt(), any(), any(), any(), any());
        doReturn(0L).when(cacheBO).invalidateFrom(any(), anyInt());

        ConnectorPreviewBO previewBO = new ConnectorPreviewBO();
        previewBO.cacheBO = cacheBO;
        previewBO.extractBO = extractBO;
        previewBO.transformationBO = transformationBO;
        previewBO.functionRunnerBO = mock(FunctionRunnerBO.class);

        List<Map<String, Object>> appOutput = List.of(row("patient-1", "combined"));
        previewBO.warmFromRun(config, (transformer, rows) -> appOutput);

        // The app step is stored with what the run's container returned, and the built-in step
        // after it is computed on that output instead of being blocked behind an unrun app step.
        verify(cacheBO).store(eq(3L), eq(1), any(), any(), eq(appOutput), any());
        verify(cacheBO).store(eq(3L), eq(2), any(), any(), eq(appOutput), any());
    }

    @Test
    void aBuiltInStepCachesTheColumnsItAddsInTheirOrder() {
        ConnectorTransformerDTO split = new ConnectorTransformerDTO();
        ConnectorConfigDTO config = new ConnectorConfigDTO();
        config.setConnectorId(3L);
        config.setTransformer(List.of(split));

        ConnectorExtractBO extractBO = mock(ConnectorExtractBO.class);
        when(extractBO.loadPreviewData(isNull(), isNull(), anyInt(), isNull(), isNull(), isNull()))
                .thenReturn(new TableData(
                        List.of("patient_nbr", "race"),
                        new ArrayList<>(List.of(row("patient-1", "Caucasian"))),
                        new ArrayList<>()));
        ConnectorTransformationBO transformationBO = mock(ConnectorTransformationBO.class);
        when(transformationBO.applyBuiltInTransformation(same(split), anyList(), isNull()))
                .thenAnswer(invocation -> {
                    List<Map<String, Object>> rows = invocation.getArgument(1);
                    return rows.stream().map(input -> {
                        Map<String, Object> output = new LinkedHashMap<>(input);
                        output.put("race_main", "Caucasian");
                        output.put("race_detail", "");
                        return output;
                    }).toList();
                });

        ConnectorPreviewTransformationCacheBO cacheBO =
                mock(ConnectorPreviewTransformationCacheBO.class, CALLS_REAL_METHODS);
        doReturn(Optional.empty()).when(cacheBO).find(any(), anyInt(), any());
        doNothing().when(cacheBO).store(any(), anyInt(), any(), any(), any(), any());

        ConnectorPreviewBO previewBO = new ConnectorPreviewBO();
        previewBO.cacheBO = cacheBO;
        previewBO.extractBO = extractBO;
        previewBO.fileHandlerBO = mock(TabularFileReaderBO.class);
        previewBO.transformationBO = transformationBO;
        previewBO.functionRunnerBO = mock(FunctionRunnerBO.class);

        PreviewResponseDTO response = previewBO.preview(config);

        // The cache keeps rows as jsonb, which drops their key order; the stored column list is what
        // restores it, so it has to name the columns the step added too, in the order it added them.
        verify(cacheBO).store(eq(3L), eq(1), any(), any(), anyList(),
                eq(List.of("patient_nbr", "race", "race_main", "race_detail")));
        assertEquals(List.of(List.of("patient_nbr", "race", "race_main", "race_detail")), response.getColumns());
    }

    @Test
    void previewShowsTheRequestedNumberOfRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            rows.add(row("patient-" + i, "Caucasian"));
        }
        ConnectorExtractBO extractBO = mock(ConnectorExtractBO.class);
        when(extractBO.loadPreviewData(isNull(), isNull(), anyInt(), isNull(), isNull(), isNull()))
                .thenReturn(new TableData(List.of("patient_nbr", "race"), rows, new ArrayList<>()));
        TabularFileReaderBO fileHandlerBO = mock(TabularFileReaderBO.class);
        when(fileHandlerBO.getJson(any(TableData.class)))
                .thenAnswer(invocation -> Integer.toString(invocation.getArgument(0, TableData.class).getRows().size()));

        ConnectorPreviewBO previewBO = new ConnectorPreviewBO();
        previewBO.cacheBO = new ConnectorPreviewTransformationCacheBO();
        previewBO.extractBO = extractBO;
        previewBO.fileHandlerBO = fileHandlerBO;

        ConnectorConfigDTO config = new ConnectorConfigDTO();
        config.setPreviewRows(25);
        PreviewResponseDTO response = previewBO.previewPivot(config);

        assertEquals(List.of("25"), response.getJsons());
        verify(extractBO).loadPreviewData(isNull(), isNull(), eq(25), isNull(), isNull(), isNull());
    }

    @Test
    void previewKeepsTenRowsWhenNoRowCountIsGiven() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            rows.add(row("patient-" + i, "Caucasian"));
        }
        ConnectorExtractBO extractBO = mock(ConnectorExtractBO.class);
        when(extractBO.loadPreviewData(isNull(), isNull(), anyInt(), isNull(), isNull(), isNull()))
                .thenReturn(new TableData(List.of("patient_nbr", "race"), rows, new ArrayList<>()));
        TabularFileReaderBO fileHandlerBO = mock(TabularFileReaderBO.class);
        when(fileHandlerBO.getJson(any(TableData.class)))
                .thenAnswer(invocation -> Integer.toString(invocation.getArgument(0, TableData.class).getRows().size()));

        ConnectorPreviewBO previewBO = new ConnectorPreviewBO();
        previewBO.cacheBO = new ConnectorPreviewTransformationCacheBO();
        previewBO.extractBO = extractBO;
        previewBO.fileHandlerBO = fileHandlerBO;

        assertEquals(List.of("10"), previewBO.previewPivot(new ConnectorConfigDTO()).getJsons());
        verify(extractBO).loadPreviewData(isNull(), isNull(), eq(10), isNull(), isNull(), isNull());
    }

    @Test
    void preferringRowsWithDataPicksThemFromTheWholeStoredSample() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            rows.add(row("patient-" + i, "?"));
        }
        rows.add(row("patient-30", "Caucasian"));
        ConnectorExtractBO extractBO = mock(ConnectorExtractBO.class);
        when(extractBO.loadPreviewData(isNull(), isNull(), anyInt(), isNull(), isNull(), isNull()))
                .thenReturn(new TableData(List.of("patient_nbr", "race"), rows, new ArrayList<>()));
        TabularFileReaderBO fileHandlerBO = mock(TabularFileReaderBO.class);
        when(fileHandlerBO.getJson(any(TableData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, TableData.class).getRows().stream()
                        .map(row -> String.valueOf(row.get("race")))
                        .toList()
                        .toString());

        ConnectorPreviewBO previewBO = new ConnectorPreviewBO();
        previewBO.cacheBO = new ConnectorPreviewTransformationCacheBO();
        previewBO.extractBO = extractBO;
        previewBO.fileHandlerBO = fileHandlerBO;

        ConnectorConfigDTO config = new ConnectorConfigDTO();
        config.setPreviewRows(3);
        config.setPreviewNonEmpty(true);
        PreviewResponseDTO response = previewBO.previewPivot(config);

        // The only row with a race is picked, and the rows stay in file order.
        assertEquals(List.of("[?, ?, Caucasian]"), response.getJsons());
        verify(extractBO).loadPreviewData(isNull(), isNull(),
                eq(bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadSettingsDTO.PREVIEW_POOL_ROWS),
                isNull(), isNull(), isNull());
    }

    @Test
    void patientPreviewsUseTheRowCountAsPatientLimitAndIgnoreTheDataPreference() {
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        ConnectorConfigDTO config = new ConnectorConfigDTO();
        config.setTransformer(List.of(transformer));
        config.setPreviewRows(2);
        config.setPreviewNonEmpty(true);

        ConnectorExtractBO extractBO = mock(ConnectorExtractBO.class);
        when(extractBO.loadPreviewData(isNull(), isNull(), anyInt(), isNull(), isNull(), isNull()))
                .thenReturn(new TableData(List.of("patient_nbr", "race"), new ArrayList<>(List.of(
                        row("patient-1", "?"),
                        row("patient-2", "?"),
                        row("patient-3", "Caucasian"))), new ArrayList<>()));
        MappingBO mappingBO = mock(MappingBO.class);
        when(mappingBO.resolveExternalIdSourceColumn(isNull())).thenReturn("patient_nbr");
        FunctionRunnerBO functionRunnerBO = mock(FunctionRunnerBO.class);
        when(functionRunnerBO.mode(same(transformer))).thenReturn(FunctionExecutionMode.PATIENT);
        ConnectorTransformationBO transformationBO = mock(ConnectorTransformationBO.class);
        when(transformationBO.applyBuiltInTransformation(same(transformer), anyList(), eq("patient_nbr")))
                .thenAnswer(invocation -> invocation.getArgument(1));
        TabularFileReaderBO fileHandlerBO = mock(TabularFileReaderBO.class);
        when(fileHandlerBO.getJson(any(TableData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, TableData.class).getRows().stream()
                        .map(row -> String.valueOf(row.get("patient_nbr")))
                        .toList()
                        .toString());

        ConnectorPreviewBO previewBO = new ConnectorPreviewBO();
        previewBO.cacheBO = new ConnectorPreviewTransformationCacheBO();
        previewBO.extractBO = extractBO;
        previewBO.fileHandlerBO = fileHandlerBO;
        previewBO.transformationBO = transformationBO;
        previewBO.functionRunnerBO = functionRunnerBO;
        previewBO.mappingBO = mappingBO;

        PreviewResponseDTO response = previewBO.preview(config);

        // Whole patients in file order: the selector would have split them, so it is not used.
        assertEquals(List.of("[patient-1, patient-2]"), response.getJsons());
        verify(extractBO).loadPreviewData(isNull(), isNull(), eq(2), isNull(), isNull(), isNull());
    }

    @Test
    void theRowCountIsClampedToTheSupportedRange() {
        ConnectorConfigDTO config = new ConnectorConfigDTO();
        assertEquals(10, config.previewRowsOrDefault());
        config.setPreviewRows(0);
        assertEquals(1, config.previewRowsOrDefault());
        config.setPreviewRows(5000);
        assertEquals(100, config.previewRowsOrDefault());
    }

    private static Map<String, Object> row(String patientId, String race) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("patient_nbr", patientId);
        row.put("race", race);
        return row;
    }
}
