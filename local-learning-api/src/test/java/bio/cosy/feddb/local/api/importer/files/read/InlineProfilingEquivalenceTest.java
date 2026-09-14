package bio.cosy.feddb.local.api.importer.files.read;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.analytics.FileAnalytics;
import bio.cosy.feddb.local.api.importer.files.table.ParsedTables;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Column statistics are now gathered while a file is parsed rather than by reading the spilled rows
 * back afterwards. That is only a safe change if it produces the same profiles: these tests pin the
 * inline result against what a second pass over the very same table produces.
 */
class InlineProfilingEquivalenceTest {

    @Test
    void inlineCsvProfilesMatchASecondPassOverTheSameRows() throws Exception {
        Path csv = writeCsv("inline-csv", """
                patient_id,age,score,label,when
                p1,40,1.5,alpha,2024-01-02
                p2,51,2.25,beta,2024-02-03
                p3,,3.5,alpha,
                p4,62,NA,gamma,2024-04-05
                p5,40,4.75,alpha,2024-05-06
                """);

        TabularFileReaderBO reader = TestReaders.reader();
        FileParsingSettingsDTO settings = csvSettings(true);

        TableData profiledInline = reader.getFirstTableData(csv.toFile(), TableReadSpec.of(settings, null, true), null, null, null);
        assertNotNull(profiledInline);

        try (profiledInline) {
            List<ColumnProfile> inline = profiledInline.getColumnProfiles();
            List<ColumnProfile> secondPass = secondPassProfiles(profiledInline);

            assertEquals(5, inline.size());
            assertEquals(describe(secondPass), describe(inline));
        }
    }

    /**
     * The archive path also reads its tables concurrently, so this covers both that the profiles are
     * right and that they end up attached to the table they describe.
     */
    @Test
    void inlineZipProfilesMatchASecondPassPerTable() throws Exception {
        Path archive = writeZip("inline-zip",
                Map.of(
                        "labs.csv", """
                                patient_id,value
                                p1,1
                                p2,2
                                p3,3
                                """,
                        "vitals.csv", """
                                patient_id,heart_rate,note
                                p1,60,resting
                                p2,72,active
                                p3,68,resting
                                """));

        TabularFileReaderBO reader = TestReaders.reader();
        ParsedTables tables = reader.getByFile(archive.toFile(), TableReadSpec.of(csvZipSettings(), null, true), null);

        try (tables) {
            assertEquals(2, tables.tables().size());
            for (Map.Entry<String, TableData> entry : tables.tables().entrySet()) {
                TableData table = entry.getValue();
                assertEquals(
                        describe(secondPassProfiles(table)),
                        describe(table.getColumnProfiles()),
                        "profiles for " + entry.getKey());
            }
        }
    }

    /** Profiles the finished table the way the old second pass did, for comparison. */
    private static List<ColumnProfile> secondPassProfiles(TableData table) {
        try (Stream<Map<String, Object>> rows = table.streamRows()) {
            return new FileAnalytics().profileRows(table.getColumns(), rows).getColumns();
        }
    }

    /** Renders the fields a profile is consumed by, so a mismatch names what actually differs. */
    private static List<String> describe(List<ColumnProfile> profiles) {
        List<String> described = new ArrayList<>();
        for (ColumnProfile profile : profiles) {
            described.add(String.join("|",
                    profile.name(),
                    profile.type(),
                    String.valueOf(profile.count()),
                    String.valueOf(profile.missing()),
                    String.valueOf(profile.uniqueValues()),
                    String.valueOf(profile.mean()),
                    String.valueOf(profile.std()),
                    String.valueOf(profile.min()),
                    String.valueOf(profile.p25()),
                    String.valueOf(profile.median()),
                    String.valueOf(profile.p75()),
                    String.valueOf(profile.max()),
                    String.valueOf(profile.topCategories()),
                    String.valueOf(profile.valueCounts())));
        }
        return described;
    }

    private static FileParsingSettingsDTO csvSettings(boolean firstSheetOnly) {
        FileParsingSettingsDTO settings = new FileParsingSettingsDTO();
        settings.setFileType(FileParsingType.CSV);
        settings.setHasHeader(true);
        settings.setFirstSheetOnly(firstSheetOnly);
        settings.setDelimiter(",");
        return settings;
    }

    private static FileParsingSettingsDTO csvZipSettings() {
        FileParsingSettingsDTO settings = csvSettings(false);
        settings.setFileType(FileParsingType.MULTIPLE_CSV_ZIP);
        return settings;
    }

    private static Path writeCsv(String name, String content) throws IOException {
        Path path = Files.createTempFile(name, ".csv");
        Files.writeString(path, content, StandardCharsets.UTF_8);
        path.toFile().deleteOnExit();
        return path;
    }

    private static Path writeZip(String name, Map<String, String> entries) throws IOException {
        Path path = Files.createTempFile(name, ".zip");
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
}
