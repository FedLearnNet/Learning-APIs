package bio.cosy.feddb.core.helper;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class FileHelperTest {

    @Test
    void unzipExtractsFilesIncludingNestedOnes() throws IOException {
        Path dir = Files.createTempDirectory("unzip");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("plot.html", "<html></html>");
        entries.put("tables/embedding.csv", "patientId,umap_1\n1,0.5\n");

        List<File> files = FileHelper.unzip(new ByteArrayInputStream(zip(entries)), dir);

        assertEquals(2, files.size());
        assertEquals("<html></html>", Files.readString(dir.resolve("plot.html")));
        assertTrue(Files.readString(dir.resolve("tables/embedding.csv")).startsWith("patientId"));
    }

    @Test
    void unzipRefusesEntriesOutsideOfTheTarget() throws IOException {
        Path parent = Files.createTempDirectory("unzip-parent");
        Path dir = Files.createDirectory(parent.resolve("target"));

        assertThrows(IOException.class, () ->
                FileHelper.unzip(new ByteArrayInputStream(zip(Map.of("../escaped.txt", "x"))), dir));
        assertFalse(Files.exists(parent.resolve("escaped.txt")));
    }

    @Test
    void unzipRoundTripsWhatFilesToZipByPathWrites() throws IOException {
        Path source = Files.createTempFile("round-trip", ".csv");
        Files.writeString(source, "a,b\n1,2\n");
        byte[] zip = FileHelper.filesToZipByPath(Map.of("data.csv", source.toFile()));

        Path dir = Files.createTempDirectory("unzip");
        List<File> files = FileHelper.unzip(new ByteArrayInputStream(zip), dir);

        assertEquals(List.of(dir.resolve("data.csv").toFile()), files);
        assertEquals("a,b\n1,2\n", Files.readString(dir.resolve("data.csv")));
    }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
