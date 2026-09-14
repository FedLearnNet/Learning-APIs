package unit.importer;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.local.api.importer.extract.PivotConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.extract.UploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.read.TabularFileReaderBO;
import bio.cosy.feddb.local.api.importer.files.read.TableReadSpec;
import bio.cosy.feddb.local.api.importer.files.read.TestReaders;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabularFileReaderBOTest {

    @Test
    void getFirstTableDataReturnsFirstSheetWhenMergeConfigIsNull() throws Exception {
        TabularFileReaderBO bo = createBo();
        Path workbookPath = createWorkbook();
        FileParsingSettingsDTO dto = excelSettings(false);

        TableData data = bo.getFirstTableData(workbookPath.toFile(), TableReadSpec.whole(dto), null, null, null);

        assertNotNull(data);
        assertEquals(List.of("patient_id", "age"), data.getColumns());
        assertEquals(2, data.getRows().size());
        assertEquals("p1", data.getRows().get(0).get("patient_id"));
    }

    @Test
    void getFirstTableDataMergesSheetsWhenMergeConfigIsPresent() throws Exception {
        TabularFileReaderBO bo = createBo();
        Path workbookPath = createWorkbook();
        FileParsingSettingsDTO dto = excelSettings(true);

        SheetMergeResultDTO mergeConfig = new SheetMergeResultDTO(
                "patient_uid",
                Map.of(
                        "Demographics", "patient_id",
                        "Labs", "lab_patient_id"
                ),
                "patient_uid"
        );

        TableData data = bo.getFirstTableData(workbookPath.toFile(), TableReadSpec.whole(dto), mergeConfig, null, null);

        assertNotNull(data);
        assertTrue(data.isDiskBacked());
        assertEquals(List.of("patient_uid", "age", "value"), data.getColumns());
        assertEquals(4, data.getRows().size());

        Map<String, Object> firstRow = data.getRows().get(0);
        assertEquals("p1", firstRow.get("patient_uid"));
        assertEquals("31", firstRow.get("age"));
        assertNull(firstRow.get("value"));

        Map<String, Object> thirdRow = data.getRows().get(2);
        assertEquals("p1", thirdRow.get("patient_uid"));
        assertNull(thirdRow.get("age"));
        assertEquals("5.6", thirdRow.get("value"));
    }

    @Test
    void getFirstTableDataPreviewMergesRowsByUidWhenMergeConfigIsPresent() throws Exception {
        TabularFileReaderBO bo = createBo();
        Path workbookPath = createWorkbook();
        FileParsingSettingsDTO dto = excelSettings(true);

        SheetMergeResultDTO mergeConfig = new SheetMergeResultDTO(
                "patient_uid",
                Map.of(
                        "Demographics", "patient_id",
                        "Labs", "lab_patient_id"
                ),
                "patient_uid"
        );

        TableData data = bo.getFirstTableData(workbookPath.toFile(), TableReadSpec.of(dto, null, true), mergeConfig, null, null);

        assertNotNull(data);
        assertEquals(List.of("patient_uid", "age", "value"), data.getColumns());
        assertEquals(2, data.getRows().size());

        Map<String, Object> firstRow = data.getRows().get(0);
        assertEquals("p1", firstRow.get("patient_uid"));
        assertEquals("31", firstRow.get("age"));
        assertEquals("5.6", firstRow.get("value"));
    }

    @Test
    void getFirstPreviewTableDataMergesPersistedLogicalTableSamples() throws Exception {
        TabularFileReaderBO bo = createBo();
        Map<String, TableSample> previewData = Map.of(
                "Demographics", new TableSample(
                        List.of("patient_id", "age"),
                        List.of(Map.of("patient_id", "p1", "age", "31")),
                        List.of()
                ),
                "Labs", new TableSample(
                        List.of("lab_patient_id", "value"),
                        List.of(Map.of("lab_patient_id", "p1", "value", "5.6")),
                        List.of()
                )
        );
        SheetMergeResultDTO mergeConfig = new SheetMergeResultDTO(
                "patient_uid",
                Map.of("Demographics", "patient_id", "Labs", "lab_patient_id"),
                "patient_uid"
        );

        TableData data = bo.getFirstPreviewTableData(previewData, excelSettings(false), 10, mergeConfig, null);

        assertEquals(1, data.longSize());
        assertEquals("p1", data.getRows().getFirst().get("patient_uid"));
        assertEquals("31", data.getRows().getFirst().get("age"));
        assertEquals("5.6", data.getRows().getFirst().get("value"));
    }

    @Test
    void previewLimitStillScansLaterRowsForAlreadySelectedUid() throws Exception {
        TabularFileReaderBO bo = createBo();
        Map<String, TableSample> previewData = new LinkedHashMap<>();
        previewData.put("Demographics", new TableSample(
                List.of("patient_id", "age"),
                List.of(Map.of("patient_id", "p1", "age", "31")),
                List.of()
        ));
        previewData.put("Labs", new TableSample(
                List.of("lab_patient_id", "value"),
                List.of(
                        Map.of("lab_patient_id", "p2", "value", "8.2"),
                        Map.of("lab_patient_id", "p1", "value", "5.6")
                ),
                List.of()
        ));
        SheetMergeResultDTO mergeConfig = new SheetMergeResultDTO(
                "patient_uid",
                Map.of("Demographics", "patient_id", "Labs", "lab_patient_id"),
                "patient_uid"
        );

        try (TableData data = bo.getFirstPreviewTableData(previewData, excelSettings(false), 1, mergeConfig, null)) {
            assertEquals(1, data.longSize());
            assertEquals("p1", data.getRows().getFirst().get("patient_uid"));
            assertEquals("5.6", data.getRows().getFirst().get("value"));
        }
    }

    @Test
    void getFirstTableDataPreviewStopsAfterRequestedShowRows() throws Exception {
        TabularFileReaderBO bo = createBo();
        Path workbookPath = createWorkbook();
        FileParsingSettingsDTO dto = excelSettings(true);

        SheetMergeResultDTO mergeConfig = new SheetMergeResultDTO(
                "patient_uid",
                Map.of(
                        "Demographics", "patient_id",
                        "Labs", "lab_patient_id"
                ),
                "patient_uid"
        );

        TableData data = bo.getFirstTableData(workbookPath.toFile(), TableReadSpec.of(dto, 1, true), mergeConfig, null, null);

        assertNotNull(data);
        assertEquals(1, data.getRows().size());
        assertEquals("p1", data.getRows().getFirst().get("patient_uid"));
        assertEquals("31", data.getRows().getFirst().get("age"));
        assertEquals("5.6", data.getRows().getFirst().get("value"));
    }

    @Test
    void getFirstTableDataAppliesUploadInfoBeforeCalculatingStatistics() throws Exception {
        TabularFileReaderBO bo = createBo();
        Path csvPath = Files.createTempFile("upload-info-statistics", ".csv");
        Files.writeString(csvPath, "age,ignored\n31,x\n42,y\n");

        FileParsingSettingsDTO settings = new FileParsingSettingsDTO();
        settings.setFileType(FileParsingType.CSV);
        settings.setHasHeader(true);
        settings.setFirstSheetOnly(true);
        settings.setDelimiter(",");
        UploadInfoDTO uploadInfo = new UploadInfoDTO(
                List.of("years", "ignored"),
                List.of(false, true),
                List.of("age", "ignored"),
                null
        );

        try (TableData data = bo.getFirstTableData(
                csvPath.toFile(), TableReadSpec.whole(settings), null, null, Map.of("0", uploadInfo))) {
            List<ColumnProfile> profiles = bo.getColumnProfiles(data);

            assertEquals(List.of("years"), data.getColumns());
            assertEquals(List.of("years"), profiles.stream().map(ColumnProfile::name).toList());
            assertEquals(2L, profiles.getFirst().count());
        }
    }

    @Test
    void getFirstTableDataMergesSheetsWithBomPrefixedUidConfig() throws Exception {
        TabularFileReaderBO bo = createBo();
        Path workbookPath = createWorkbook();
        FileParsingSettingsDTO dto = excelSettings(true);

        SheetMergeResultDTO mergeConfig = new SheetMergeResultDTO(
                "\uFEFFpatient_uid",
                Map.of(
                        "Demographics", "\uFEFFpatient_id",
                        "Labs", "\uFEFFlab_patient_id"
                ),
                "\uFEFFpatient_uid"
        );

        TableData data = bo.getFirstTableData(workbookPath.toFile(), TableReadSpec.whole(dto), mergeConfig, null, null);

        assertNotNull(data);
        assertEquals(List.of("patient_uid", "age", "value"), data.getColumns());
        assertEquals(4, data.getRows().size());
        assertEquals("p1", data.getRows().getFirst().get("patient_uid"));
    }

    @Test
    void getFirstTableDataMergesCsvZipDirectlyToDisk() throws Exception {
        TabularFileReaderBO bo = createBo();
        Path zipPath = createCsvZip();
        FileParsingSettingsDTO dto = csvZipSettings();

        SheetMergeResultDTO mergeConfig = new SheetMergeResultDTO(
                "patient_id",
                Map.of(
                        "SUR_demographics", "patient_id",
                        "SUR_labs", "patient_id"
                ),
                "patient_id"
        );

        TableData data = bo.getFirstTableData(zipPath.toFile(), TableReadSpec.whole(dto), mergeConfig, null, null);

        assertNotNull(data);
        assertTrue(data.isDiskBacked());
        assertEquals(List.of("patient_id", "age", "lab_name", "lab_value"), data.getColumns());
        assertEquals(4, data.longSize());

        List<Map<String, Object>> rows = data.getRows();
        assertEquals("p1", rows.getFirst().get("patient_id"));
        assertEquals("31", rows.getFirst().get("age"));
        assertNull(rows.getFirst().get("lab_name"));
        assertEquals("glucose", rows.get(2).get("lab_name"));
    }

    @Test
    void getFirstTableDataSkipsLeadingBlankExcelRows() throws Exception {
        TabularFileReaderBO bo = createBo();
        Path workbookPath = createWorkbookWithLeadingBlankRows();
        FileParsingSettingsDTO dto = excelSettings(true);

        TableData data = bo.getFirstTableData(workbookPath.toFile(), TableReadSpec.whole(dto), null, null, null);

        assertNotNull(data);
        assertEquals(List.of("patient_id", "age"), data.getColumns());
        assertEquals(2, data.getRows().size());
        assertEquals("p1", data.getRows().getFirst().get("patient_id"));
    }

    @Test
    void getFirstTableDataPivotsSingleCsv() throws Exception {
        TabularFileReaderBO bo = createBo();
        Path csvPath = Files.createTempFile("single-pivot", ".csv");
        Files.writeString(csvPath, "field,patient-1,patient-2\npatient_id,p1,p2\nage,31,42\n");

        FileParsingSettingsDTO dto = new FileParsingSettingsDTO();
        dto.setFileType(FileParsingType.CSV);
        dto.setHasHeader(true);
        dto.setFirstSheetOnly(true);
        dto.setDelimiter(",");

        TableData data = bo.getFirstTableData(csvPath.toFile(), TableReadSpec.whole(dto), null,
                new PivotConfigDTO(Map.of("key-is-ignored", 0)), null);
        try {
            assertTrue(data.isDiskBacked());
            assertEquals(List.of("field", "patient_id", "age"), data.getColumns());
            assertEquals("p1", data.getRows().getFirst().get("patient_id"));
        } finally {
            data.close();
        }
    }

    @Test
    void getFirstTableDataPivotsBeforeMergingSheets() throws Exception {
        TabularFileReaderBO bo = createBo();
        Path workbookPath = createPivotWorkbook();
        FileParsingSettingsDTO dto = excelSettings(false);

        SheetMergeResultDTO mergeConfig = new SheetMergeResultDTO(
                "patient_uid",
                Map.of(
                        "Demographics", "patient_id",
                        "Labs", "lab_patient_id"
                ),
                "patient_uid"
        );
        PivotConfigDTO pivotConfig = new PivotConfigDTO(Map.of(
                "Demographics", 0,
                "Labs", 0
        ));

        TableData data = bo.getFirstTableData(
                workbookPath.toFile(), TableReadSpec.whole(dto), mergeConfig, pivotConfig, null);
        try {
            assertTrue(data.isDiskBacked());
            assertEquals(
                    List.of("patient_uid", "Demographics::field", "age", "Labs::field", "value"),
                    data.getColumns()
            );
            assertEquals(4, data.longSize());
            assertEquals("p1", data.getRows().getFirst().get("patient_uid"));
            assertEquals("31", data.getRows().getFirst().get("age"));
            assertEquals("5.6", data.getRows().get(2).get("value"));
        } finally {
            data.close();
        }
    }

    @Test
    void getFirstTableDataPreviewsPivotedAndMergedSheets() throws Exception {
        TabularFileReaderBO bo = createBo();
        Path workbookPath = createPivotWorkbook();
        FileParsingSettingsDTO dto = excelSettings(false);
        SheetMergeResultDTO mergeConfig = new SheetMergeResultDTO(
                "patient_uid",
                Map.of("Demographics", "patient_id", "Labs", "lab_patient_id"),
                "patient_uid"
        );
        PivotConfigDTO pivotConfig = new PivotConfigDTO(Map.of("Demographics", 0, "Labs", 0));

        TableData data = bo.getFirstTableData(
                workbookPath.toFile(), TableReadSpec.of(dto, 1, true), mergeConfig, pivotConfig, null);
        try {
            assertEquals(1, data.longSize());
            assertEquals("p1", data.getRows().getFirst().get("patient_uid"));
            assertEquals("31", data.getRows().getFirst().get("age"));
            assertEquals("5.6", data.getRows().getFirst().get("value"));
        } finally {
            data.close();
        }
    }

    private TabularFileReaderBO createBo() {
        return TestReaders.reader();
    }

    private FileParsingSettingsDTO excelSettings(boolean firstSheetOnly) {
        FileParsingSettingsDTO dto = new FileParsingSettingsDTO();
        dto.setFileType(FileParsingType.EXCEL);
        dto.setHasHeader(true);
        dto.setFirstSheetOnly(firstSheetOnly);
        return dto;
    }

    private FileParsingSettingsDTO csvZipSettings() {
        FileParsingSettingsDTO dto = new FileParsingSettingsDTO();
        dto.setFileType(FileParsingType.MULTIPLE_CSV_ZIP);
        dto.setHasHeader(true);
        dto.setFirstSheetOnly(false);
        dto.setDelimiter(",");
        return dto;
    }

    private Path createWorkbook() throws IOException {
        Path path = Files.createTempFile("merge-config", ".xlsx");

        try (Workbook workbook = new XSSFWorkbook();
             OutputStream outputStream = Files.newOutputStream(path)) {
            var demographics = workbook.createSheet("Demographics");
            var demoHeader = demographics.createRow(0);
            demoHeader.createCell(0).setCellValue("patient_id");
            demoHeader.createCell(1).setCellValue("age");
            writeRow(demographics, 1, Map.of("patient_id", "p1", "age", "31"), List.of("patient_id", "age"));
            writeRow(demographics, 2, Map.of("patient_id", "p2", "age", "42"), List.of("patient_id", "age"));

            var labs = workbook.createSheet("Labs");
            var labsHeader = labs.createRow(0);
            labsHeader.createCell(0).setCellValue("lab_patient_id");
            labsHeader.createCell(1).setCellValue("value");
            writeRow(labs, 1, Map.of("lab_patient_id", "p1", "value", "5.6"), List.of("lab_patient_id", "value"));
            writeRow(labs, 2, Map.of("lab_patient_id", "p2", "value", "7.1"), List.of("lab_patient_id", "value"));

            workbook.write(outputStream);
        }

        return path;
    }

    private Path createPivotWorkbook() throws IOException {
        Path path = Files.createTempFile("pivot-before-merge", ".xlsx");

        try (Workbook workbook = new XSSFWorkbook();
             OutputStream outputStream = Files.newOutputStream(path)) {
            var demographics = workbook.createSheet("Demographics");
            writePivotRow(demographics, 0, "field", "patient-1", "patient-2");
            writePivotRow(demographics, 1, "patient_id", "p1", "p2");
            writePivotRow(demographics, 2, "age", "31", "42");

            var labs = workbook.createSheet("Labs");
            writePivotRow(labs, 0, "field", "patient-1", "patient-2");
            writePivotRow(labs, 1, "lab_patient_id", "p1", "p2");
            writePivotRow(labs, 2, "value", "5.6", "7.1");

            workbook.write(outputStream);
        }

        return path;
    }

    private void writePivotRow(org.apache.poi.ss.usermodel.Sheet sheet, int index, String... values) {
        var row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    private Path createCsvZip() throws IOException {
        Path path = Files.createTempFile("merge-config", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            writeZipEntry(zip, "SUR_demographics.csv", "patient_id,age\np1,31\np2,42\n");
            writeZipEntry(zip, "SUR_labs.csv", "patient_id,lab_name,lab_value\np1,glucose,5.6\np2,hba1c,7.1\n");
        }
        return path;
    }

    private void writeZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private Path createWorkbookWithLeadingBlankRows() throws IOException {
        Path path = Files.createTempFile("leading-blank-rows", ".xlsx");

        try (Workbook workbook = new XSSFWorkbook();
             OutputStream outputStream = Files.newOutputStream(path)) {
            var sheet = workbook.createSheet("Patients");
            sheet.createRow(0).createCell(0).setCellValue("");
            sheet.createRow(1);

            var header = sheet.createRow(2);
            header.createCell(0).setCellValue("patient_id");
            header.createCell(1).setCellValue("age");
            writeRow(sheet, 3, Map.of("patient_id", "p1", "age", "31"), List.of("patient_id", "age"));
            writeRow(sheet, 4, Map.of("patient_id", "p2", "age", "42"), List.of("patient_id", "age"));

            workbook.write(outputStream);
        }

        return path;
    }

    private void writeRow(org.apache.poi.ss.usermodel.Sheet sheet, int rowIndex, Map<String, String> values, List<String> columns) {
        var row = sheet.createRow(rowIndex);
        for (int i = 0; i < columns.size(); i++) {
            String value = values.get(columns.get(i));
            row.createCell(i).setCellValue(value);
        }
    }
}
