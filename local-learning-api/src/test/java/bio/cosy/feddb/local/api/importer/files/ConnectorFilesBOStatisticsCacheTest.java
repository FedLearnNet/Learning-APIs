package bio.cosy.feddb.local.api.importer.files;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.importer.files.read.FileAnalysis;
import bio.cosy.feddb.local.api.importer.files.read.FileAnalysisBO;
import bio.cosy.feddb.local.api.importer.files.read.TabularFileReaderBO;
import bio.cosy.feddb.local.api.importer.files.read.TableReadSpec;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.files.table.ParsedTables;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectorFilesBOStatisticsCacheTest {

    @Test
    void fileAnalysisStoresAnIndependentTenRowTableDataPreview() {
        TabularFileReaderBO fileHandler = mock(TabularFileReaderBO.class);
        FileAnalysisBO bo = new FileAnalysisBO(fileHandler);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            rows.add(new LinkedHashMap<>(Map.of("patient_id", "p" + i, "age", i)));
        }
        TableData parsed = new TableData(List.of("patient_id", "age"), rows, List.of(profile("age")));
        when(fileHandler.getByFile(any(), any(), isNull()))
                .thenReturn(ParsedTables.single(parsed));
        when(fileHandler.getJson(any(TableData.class)))
                .thenAnswer(invocation -> Integer.toString(
                        invocation.getArgument(0, TableData.class).getRows().size()));

        FileAnalysis analysis = bo.analyze(
                new File("ignored.csv"),
                TableReadSpec.forAnalysis(new FileParsingSettingsDTO(), 20, true, true),
                null,
                20);

        assertEquals("20", analysis.uploadInfo().getFirst().getJson());
        assertEquals(10, analysis.previewData().get("0").rows().size());
        assertEquals(List.of("patient_id", "age"), analysis.previewData().get("0").columns());
        assertEquals(profile("age"), analysis.previewData().get("0").columnProfiles().getFirst());
        verify(fileHandler).getByFile(any(), any(), isNull());
    }

    @Test
    void parsingSettingsIgnoreLegacyUploadOnlyFields() throws Exception {
        FileParsingSettingsDTO settings = new ObjectMapper().readValue("""
                {
                  "fileType":"CSV",
                  "delimiter":",",
                  "hasHeader":true,
                  "firstSheetOnly":true,
                  "previewRows":10,
                  "supportFile":false
                }
                """, FileParsingSettingsDTO.class);

        assertEquals(FileParsingType.CSV, settings.getFileType());
        assertEquals(",", settings.getDelimiter());
    }

    @Test
    void columnProfileRoundTripsThroughDatabaseJsonShape() throws Exception {
        ColumnProfile profile = profile("age");
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(List.of(profile));
        List<ColumnProfile> restored = mapper.readValue(
                json,
                new TypeReference<>() {
                }
        );

        assertEquals(profile, restored.getFirst());
    }

    @Test
    void tableDataPreviewRoundTripsThroughDatabaseJsonShape() throws Exception {
        TableSample preview = new TableSample(
                List.of("patient_id", "age"),
                List.of(Map.of("patient_id", "p1", "age", 42)),
                List.of(profile("age"))
        );
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(Map.of("Patients", preview));
        Map<String, TableSample> restored = mapper.readValue(json, new TypeReference<>() {
        });

        assertEquals(preview, restored.get("Patients"));
    }

    @Test
    void readsTemporaryCacheDtoEntryShape() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = """
                [{
                  "name":"age","type":"TEXT","count":1,"missing":0,"uniqueValues":1,
                  "topCategories":[{"value":"42","count":1}],
                  "valueCounts":[{"value":"42","count":1}]
                }]
                """;

        List<ColumnProfile> restored = mapper.readValue(json, new TypeReference<>() {
        });

        assertEquals(profile("age"), restored.getFirst());
    }

    private ColumnProfile profile(String name) {
        return new ColumnProfile(
                name, "TEXT", 1, 0, 1,
                null, null, null, null, null, null, null,
                List.of(Map.entry("42", 1)), List.of(Map.entry("42", 1))
        );
    }
}
