package bio.cosy.feddb.local.api.importer.files.read;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sample kept with a table is bounded, but the statistics beside it are not: they describe the
 * whole file. Computing them from the preview rows instead would be the easy mistake, and one the
 * numbers on screen give no hint of - a count of 10 out of 250 rows looks exactly like a count.
 */
class SheetStatisticsTest {

    private static final int ROWS_PER_SHEET = 250;
    private static final int PREVIEW_ROWS = 10;

    @Test
    void profilesCoverTheWholeFileRatherThanThePreview() throws Exception {
        Path archive = archiveWithTwoTables();
        FileAnalysisBO analysisBO = createAnalysisBO();

        FileAnalysis analysis = analysisBO.analyze(archive.toFile(),
                TableReadSpec.forAnalysis(zipSettings(), PREVIEW_ROWS, true, true), null, PREVIEW_ROWS);

        assertEquals(2, analysis.uploadInfo().size());

        for (ConnectorFileUploadInfoDTO info : analysis.uploadInfo()) {
            assertTrue(info.getColumnProfiles().stream()
                            .allMatch(profile -> profile.count() == ROWS_PER_SHEET),
                    "statistics for " + info.getSheet() + " should cover all " + ROWS_PER_SHEET
                            + " rows, got " + profileText(info));
        }
    }

    private static List<String> profileText(ConnectorFileUploadInfoDTO info) {
        List<String> text = new ArrayList<>();
        for (ColumnProfile profile : info.getColumnProfiles()) {
            text.add(profile.name() + ":" + profile.type() + ":" + profile.count()
                    + ":" + profile.missing() + ":" + profile.uniqueValues());
        }
        return text;
    }

    private static Path archiveWithTwoTables() throws Exception {
        StringBuilder labs = new StringBuilder("subject_id,item,charttime,valuenum\n");
        StringBuilder vitals = new StringBuilder("subject_id,heart_rate\n");
        for (int i = 0; i < ROWS_PER_SHEET; i++) {
            labs.append("P").append(i % 50).append(',')
                    .append(50800 + (i % 7)).append(',')
                    .append("2024-03-0").append(1 + (i % 9)).append("T08:15:00,")
                    .append(i % 40 == 0 ? "" : String.valueOf(i / 10.0))
                    .append('\n');
            vitals.append("P").append(i % 50).append(',').append(60 + (i % 30)).append('\n');
        }

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("labs.csv", labs.toString());
        entries.put("vitals.csv", vitals.toString());

        Path path = Files.createTempFile("sheet-stats", ".zip");
        try (OutputStream out = Files.newOutputStream(path);
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        path.toFile().deleteOnExit();
        return path;
    }

    private static FileParsingSettingsDTO zipSettings() {
        FileParsingSettingsDTO settings = new FileParsingSettingsDTO();
        settings.setFileType(FileParsingType.MULTIPLE_CSV_ZIP);
        settings.setHasHeader(true);
        settings.setFirstSheetOnly(false);
        settings.setDelimiter(",");
        return settings;
    }

    private static FileAnalysisBO createAnalysisBO() {
        return new FileAnalysisBO(TestReaders.reader());
    }
}
