package bio.cosy.feddb.core.agent.pojo;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.nio.file.Files;
import java.nio.file.Path;

public class LocalTestFileUpload implements FileUpload {

    private final String name;
    private final String fileName;
    private final Path filePath;
    private final String contentType;

    public LocalTestFileUpload(String name, String fileName, Path filePath, String contentType) {
        this.name = name;
        this.fileName = fileName;
        this.filePath = filePath;
        this.contentType = contentType;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Path filePath() {
        return filePath;
    }

    @Override
    public String fileName() {
        return fileName;
    }

    @Override
    public long size() {
        try {
            return Files.size(filePath);
        } catch (Exception e) {
            return 0L;
        }
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public String charSet() {
        return "UTF-8";
    }

    @Override
    public Path uploadedFile() {
        return filePath;
    }

    @Override
    public MultivaluedMap<String, String> getHeaders() {
        return new MultivaluedHashMap<>();
    }
}
