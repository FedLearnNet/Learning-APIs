package bio.cosy.feddb.local.api.importer.files.upload;

import jakarta.ws.rs.BadRequestException;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpaquePathUploadValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void acceptsZipOfExcelMembers() throws Exception {
        Path zip = tempDir.resolve("sample_batch.zip");
        writeZip(zip, "a.xlsx", "PK\u0003\u0004fake".getBytes(StandardCharsets.ISO_8859_1));

        OpaquePathUploadValidator.ValidatedOpaqueFile validated =
                OpaquePathUploadValidator.validate(upload("sample_batch.zip", zip));

        assertEquals("sample_batch.zip", validated.safeFileName());
        assertEquals("application/zip", validated.contentType());
        assertTrue(validated.size() > 0);
    }

    @Test
    void stripsPathComponentsFromFileName() throws Exception {
        Path zip = tempDir.resolve("evil.zip");
        writeZip(zip, "ok.xlsx", "PK\u0003\u0004x".getBytes(StandardCharsets.ISO_8859_1));

        OpaquePathUploadValidator.ValidatedOpaqueFile validated =
                OpaquePathUploadValidator.validate(upload("../evil.zip", zip));

        assertEquals("evil.zip", validated.safeFileName());
    }

    @Test
    void rejectsZipSlipEntries() throws Exception {
        Path zip = tempDir.resolve("slip.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("../escape.xlsx"));
            zos.write(new byte[]{'P', 'K', 3, 4});
            zos.closeEntry();
        }

        assertThrows(BadRequestException.class,
                () -> OpaquePathUploadValidator.validate(upload("slip.zip", zip)));
    }

    @Test
    void rejectsNonZipMagic() throws Exception {
        Path file = tempDir.resolve("nope.zip");
        Files.writeString(file, "not-a-zip");

        assertThrows(BadRequestException.class,
                () -> OpaquePathUploadValidator.validate(upload("nope.zip", file)));
    }

    @Test
    void rejectsDisallowedExtension() throws Exception {
        Path file = tempDir.resolve("payload.exe");
        Files.write(file, new byte[]{'P', 'K', 3, 4, 0});

        assertThrows(BadRequestException.class,
                () -> OpaquePathUploadValidator.validate(upload("payload.exe", file)));
    }

    @Test
    void acceptsCsvWithoutZipMagic() throws Exception {
        Path file = tempDir.resolve("patients.csv");
        Files.writeString(file, "patient_id,value\n1,2\n");

        OpaquePathUploadValidator.ValidatedOpaqueFile validated =
                OpaquePathUploadValidator.validate(upload("patients.csv", file));

        assertEquals("patients.csv", validated.safeFileName());
        assertEquals("text/csv", validated.contentType());
    }

    @Test
    void volumeObjectNameUsesVariableAndExtension() {
        assertEquals("ffq_archive.zip",
                OpaquePathUploadValidator.volumeObjectName("ffq_archive", "sample_batch.zip"));
        assertEquals("ffq_archive.xlsx",
                OpaquePathUploadValidator.volumeObjectName("ffq_archive", "patient.xlsx"));
        assertEquals("ffq_archive_.zip",
                OpaquePathUploadValidator.volumeObjectName("ffq archive!", "x.zip"));
    }

    private static FileUpload upload(String name, Path path) {
        FileUpload upload = mock(FileUpload.class);
        when(upload.fileName()).thenReturn(name);
        when(upload.uploadedFile()).thenReturn(path);
        return upload;
    }

    private static void writeZip(Path zip, String entryName, byte[] content) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content);
            zos.closeEntry();
        }
    }
}
