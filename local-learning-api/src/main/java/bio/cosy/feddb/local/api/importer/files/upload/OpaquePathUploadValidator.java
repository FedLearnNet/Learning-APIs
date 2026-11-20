package bio.cosy.feddb.local.api.importer.files.upload;

import jakarta.ws.rs.BadRequestException;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Validates opaque PATH uploads before they are stored.
 *
 * <p>These files are never tabular-parsed by the importer; tools read them later.
 * Validation is deliberately strict: allowlisted extensions, ZIP magic bytes,
 * size caps, sanitized display names, and light ZIP-bomb / zip-slip guards.</p>
 */
public final class OpaquePathUploadValidator {

    /** Matches local-api HTTP body ceiling with headroom for multipart overhead. */
    public static final long MAX_BYTES = 512L * 1024 * 1024;

    private static final int MAX_ZIP_ENTRIES = 200_000;
    private static final long MAX_ZIP_UNCOMPRESSED_BYTES = 8L * 1024 * 1024 * 1024;
    private static final double MAX_COMPRESSION_RATIO = 200.0;

    /** PATH tools may accept spreadsheets, archives, or CSV blobs without tabular profiling. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".xlsx", ".xlsm", ".zip", ".csv");

    private static final Set<String> ZIP_CONTAINER_EXTENSIONS = Set.of(".xlsx", ".xlsm", ".zip");

    private OpaquePathUploadValidator() {
    }

    public record ValidatedOpaqueFile(String safeFileName, String contentType, long size, Path path) {
    }

    public static ValidatedOpaqueFile validate(FileUpload upload) {
        if (upload == null || upload.uploadedFile() == null) {
            throw new BadRequestException("No file provided for opaque PATH upload");
        }
        Path path = upload.uploadedFile();
        if (!Files.isRegularFile(path)) {
            throw new BadRequestException("Uploaded opaque PATH file is missing");
        }

        long size;
        try {
            size = Files.size(path);
        } catch (IOException e) {
            throw new BadRequestException("Could not read opaque PATH upload size");
        }
        if (size <= 0) {
            throw new BadRequestException("Opaque PATH upload is empty");
        }
        if (size > MAX_BYTES) {
            throw new BadRequestException(
                    "Opaque PATH upload exceeds the maximum allowed size of " + MAX_BYTES + " bytes");
        }

        String originalName = upload.fileName() == null ? "upload.bin" : upload.fileName();
        String safeName = sanitizeFileName(originalName);
        String extension = extensionOf(safeName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException(
                    "Opaque PATH uploads allow only .xlsx, .xlsm, .zip, or .csv (got '" + extension + "')");
        }

        if (ZIP_CONTAINER_EXTENSIONS.contains(extension)) {
            requireZipMagic(path);
            if (".zip".equals(extension)) {
                inspectZipArchive(path, size);
            }
        }

        return new ValidatedOpaqueFile(safeName, contentTypeFor(extension), size, path);
    }

    /**
     * Builds a volume object name from the tool input variable and the stored extension.
     * Only safe characters are kept so the name cannot escape the volume mount.
     */
    public static String volumeObjectName(String variableName, String storedFileName) {
        String base = variableName == null ? "input" : variableName.trim();
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isBlank()) {
            base = "input";
        }
        if (base.length() > 64) {
            base = base.substring(0, 64);
        }
        String extension = extensionOf(storedFileName == null ? "" : storedFileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Refusing to stage opaque input with extension '" + extension + "'");
        }
        return base + extension;
    }

    static String sanitizeFileName(String original) {
        String name = Path.of(original.replace('\\', '/')).getFileName().toString();
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            throw new BadRequestException("Opaque PATH upload has an invalid file name");
        }
        if (name.length() > 180) {
            String ext = extensionOf(name);
            String stem = name.substring(0, Math.min(180 - ext.length(), name.length() - ext.length()));
            name = stem + ext;
        }
        return name;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String contentTypeFor(String extension) {
        return switch (extension) {
            case ".xlsx", ".xlsm" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".zip" -> "application/zip";
            case ".csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }

    private static void requireZipMagic(Path path) {
        // .xlsx / .xlsm / .zip are ZIP containers (local file header PK\x03\x04 or empty archive PK\x05\x06).
        byte[] header = new byte[4];
        try (InputStream in = Files.newInputStream(path)) {
            int read = in.read(header);
            if (read < 4) {
                throw new BadRequestException("Opaque PATH upload is too small to be a valid archive");
            }
        } catch (IOException e) {
            throw new BadRequestException("Could not read opaque PATH upload header");
        }
        boolean zipLocal = header[0] == 'P' && header[1] == 'K' && header[2] == 3 && header[3] == 4;
        boolean zipEmpty = header[0] == 'P' && header[1] == 'K' && header[2] == 5 && header[3] == 6;
        if (!zipLocal && !zipEmpty) {
            throw new BadRequestException(
                    "Opaque PATH upload content does not match a ZIP/XLSX container (bad magic bytes)");
        }
    }

    private static void inspectZipArchive(Path path, long compressedSize) {
        try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
            int entries = 0;
            long uncompressedTotal = 0;
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                entries++;
                if (entries > MAX_ZIP_ENTRIES) {
                    throw new BadRequestException(
                            "Opaque ZIP exceeds the maximum of " + MAX_ZIP_ENTRIES + " entries");
                }
                String name = entry.getName() == null ? "" : entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("..")) {
                    throw new BadRequestException(
                            "Opaque ZIP contains an unsafe entry path and was rejected");
                }
                long uncompressed = entry.getSize();
                if (uncompressed < 0) {
                    continue;
                }
                uncompressedTotal = Math.addExact(uncompressedTotal, uncompressed);
                if (uncompressedTotal > MAX_ZIP_UNCOMPRESSED_BYTES) {
                    throw new BadRequestException(
                            "Opaque ZIP uncompressed size exceeds the safety limit");
                }
                if (compressedSize > 0 && uncompressedTotal > compressedSize * MAX_COMPRESSION_RATIO) {
                    throw new BadRequestException(
                            "Opaque ZIP compression ratio exceeds the safety limit");
                }
            }
            if (entries == 0) {
                throw new BadRequestException("Opaque ZIP archive contains no entries");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (ArithmeticException e) {
            throw new BadRequestException("Opaque ZIP uncompressed size overflowed the safety limit");
        } catch (IOException e) {
            throw new BadRequestException("Opaque ZIP could not be opened for safety inspection");
        }
    }
}
