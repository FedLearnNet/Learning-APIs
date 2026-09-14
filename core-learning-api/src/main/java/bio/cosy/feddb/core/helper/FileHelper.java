package bio.cosy.feddb.core.helper;

import bio.cosy.feddb.core.api.app.config.ToolConfigDataType;
import bio.cosy.feddb.core.base.BaseFileEntity;
import jakarta.activation.MimetypesFileTypeMap;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jodd.net.MimeTypes;
import lombok.Cleanup;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.server.multipart.FormValue;

import javax.imageio.ImageIO;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


public class FileHelper {

    private static final int MAX_BYTES = 10485760; // 10MB
    private static final String ERROR_BASE_CONTENT = "Unable to read file: ";


    public static String formToBase64(FormValue value) throws IOException {
        String fileName = value.getFileName();
        MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap();
        String mimeType = fileTypeMap.getContentType(fileName);
        byte[] bytes = value.getFileItem().getInputStream().readAllBytes();
        String base64Encoded = Base64.getEncoder().encodeToString(bytes);
        String base64WithMimeType = "data:" + mimeType + ";base64," + base64Encoded;
        return base64WithMimeType;
    }

    public static String formToBase64(FileUpload value) throws IOException {
        String fileName = value.fileName();
        MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap();
        String mimeType = fileTypeMap.getContentType(fileName);
        Path p = value.uploadedFile();
        byte[] bytes = Files.readAllBytes(p);
        String base64Encoded = Base64.getEncoder().encodeToString(bytes);
        String base64WithMimeType = "data:" + mimeType + ";base64," + base64Encoded;
        return base64WithMimeType;
    }


    


    public static String pathToBase64(String path, String mimeType) {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            return null;
        }
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            byte[] bytes = inputStream.readAllBytes();
            String base64Encoded = Base64.getEncoder().encodeToString(bytes);
            String base64WithMimeType = "data:" + mimeType + ";base64," + base64Encoded;
            return base64WithMimeType;
        } catch (IOException e) {
            return null;
        }
    }

    public static String imagePathToBase64(Path imagePath) throws IOException {
        byte[] bytes = Files.readAllBytes(imagePath);
        String base64Encoded = Base64.getEncoder().encodeToString(bytes);
        String base64WithMimeType = "data:" + ToolConfigDataType.IMAGE.getMineType() + ";base64," + base64Encoded;
        return base64WithMimeType;
    }

    public static void createOutputZip(ByteArrayOutputStream byteArrayOutputStream, Map<String, Object> output) throws IOException, IllegalArgumentException {
        try (ZipOutputStream zos = new ZipOutputStream(byteArrayOutputStream)) {
            for (Map.Entry<String, Object> entry : output.entrySet()) {
                String fileName = entry.getKey();
                String value = (String) entry.getValue();
                String extension = "txt";
                if (value.contains(";base64,")) {
                    String[] parts = value.split(",", 2);
                    if (parts.length != 2) {
                        throw new IllegalArgumentException("Invalid Base64 format for: " + fileName);
                    }
                    String mimeType = parts[0]; // MIME type
                    String base64Content = parts[1]; // Base64 content

                    if (isValidBase64(base64Content)) {
                        mimeType = mimeType.replace("data:", "");
                        mimeType = mimeType.replace(";base64", "");
                        extension = getExtensionFromMimeType(mimeType);
                        if (extension == null) {
                            throw new IllegalArgumentException("Unsupported MIME type: " + mimeType);
                        }
                        byte[] fileContent = Base64.getDecoder().decode(base64Content);
                        addFileToZip(zos, fileName + "." + extension, fileContent);
                    } else {
                        addFileToZip(zos, fileName + ".txt", value.getBytes());
                    }
                } else {
                    addFileToZip(zos, fileName + ".txt", value.getBytes());
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid ZIP", e);
        }
    }

    public static byte[] filesToZip(Map<BaseFileEntity, File> fileMap) throws UncheckedIOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<BaseFileEntity, File> entry : fileMap.entrySet()) {
                File f = entry.getValue();
                if (f == null || !f.exists() || !f.isFile()) {
                    continue;
                }
                String entryName = entry.getKey().getFileName();
                ZipEntry zipEntry = new ZipEntry(entryName);
                zipEntry.setSize(f.length());
                zipEntry.setTime(f.lastModified());
                zos.putNextEntry(zipEntry);
                Files.copy(f.toPath(), zos);
                zos.closeEntry();
            }

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create file zip", e);
        }
    }

    public static List<File> unzip(InputStream zip, Path targetDir) throws IOException {
        Path root = targetDir.toAbsolutePath().normalize();
        List<File> extracted = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(zip)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("Blocked zip entry outside of the target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    extracted.add(target.toFile());
                }
                in.closeEntry();
            }
        }
        return extracted;
    }

    public static byte[] filesToZipByPath(Map<String, File> fileMap) throws UncheckedIOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (Map.Entry<String, File> entry : fileMap.entrySet()) {
                File f = entry.getValue();
                if (f == null || !f.exists() || !f.isFile()) {
                    continue;
                }

                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setSize(f.length());
                zipEntry.setTime(f.lastModified());
                zos.putNextEntry(zipEntry);
                Files.copy(f.toPath(), zos);
                zos.closeEntry();
            }

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create file zip", e);
        }
    }
    private static boolean isValidBase64(String base64Content) {
        try {
            Base64.getDecoder().decode(base64Content);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void addFileToZip(ZipOutputStream zos, String fileName, byte[] content) throws IOException {
        ZipEntry zipEntry = new ZipEntry(fileName);
        zos.putNextEntry(zipEntry);
        zos.write(content);
        zos.closeEntry();
    }

    public static DecodedFile decodeBase64FileContent(String data) throws IllegalArgumentException {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Input data is null or empty");
        }

        if (data.contains("data:") && data.contains(";base64,")) {
            data = data.split(";base64,")[1];
        }

        byte[] decodedFile;
        try {
            decodedFile = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 input", e);
        }

        String fileName = UUID.randomUUID().toString().substring(0, 12);

        String fileExtension = getFileExtension(decodedFile, fileName);

        String completeFileName = fileName + "." + fileExtension;

        return new DecodedFile(completeFileName, decodedFile);
    }

    public static String getLocalFileContent(String filePath) {
        try {
            return Files.readString(Paths.get(filePath));
        } catch (Exception e) {
            return "Unable to read local file.";
        }
    }

    private static String getExtensionFromMimeType(String mimeType) {

        String[] fileExtensions = MimeTypes.findExtensionsByMimeTypes(mimeType, false);
        if (fileExtensions.length > 0) {
            return fileExtensions[0];
        }
        return null;
    }

    public static String getFileExtension(byte[] decodedFile, String fileName) {
        String extension = null;

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(decodedFile);
            String mimeType = ImageIO.getImageReadersBySuffix(fileName).next().getFormatName();

            if ("jpeg".equalsIgnoreCase(mimeType)) {
                extension = "jpg";
            } else {
                extension = mimeType.toLowerCase();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "unknown";
        }

        return extension;
    }

    public static String getFileContentFromUrl(String url) {
        @Cleanup
        HttpClient client = HttpClient.newHttpClient();
        String content = "";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = client.send(request, BodyHandlers.ofInputStream());

            String contentLengthHeader = response.headers().firstValue("Content-Length").orElse(null);
            if (contentLengthHeader == null) {
                content = ERROR_BASE_CONTENT + "Cannot determine file size. Please make sure to specify link to raw file, e.g. in case of github.";
            } else {
                int contentLength = Integer.parseInt(contentLengthHeader);
                if (contentLength < MAX_BYTES) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                        StringBuilder contentBuilder = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            contentBuilder.append(line).append("\n");
                        }
                        content = contentBuilder.toString();

                        if ("404: Not Found".equals(content.trim())) {
                            throw new NotFoundException();
                        }
                    }
                } else {
                    content = ERROR_BASE_CONTENT + "File is too large to read... Maximum filesize: 10MB";
                }
            }

        } catch (IllegalArgumentException e) {
            throw new BadRequestException(ERROR_BASE_CONTENT + "Invalid URL");
        } catch (IOException | InterruptedException e) {
            throw new NotFoundException(ERROR_BASE_CONTENT + "File Not Found...");
        }

        return content;

    }
}
