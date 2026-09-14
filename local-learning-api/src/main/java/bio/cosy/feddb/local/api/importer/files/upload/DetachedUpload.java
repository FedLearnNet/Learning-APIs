package bio.cosy.feddb.local.api.importer.files.upload;

import io.quarkus.logging.Log;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * An uploaded file taken out of its request.
 *
 * <p>The server deletes what a multipart request uploaded as soon as that request ends. For an import
 * that outlives its request - the browser was closed, the tab navigated away - that would pull the
 * file out from under a read already in progress. Moving it first, within the same directory so the
 * move is a rename rather than a copy of gigabytes, makes the import the file's owner: it lasts as
 * long as the work does and is deleted by whoever finishes it.</p>
 */
public record DetachedUpload(String fileName, Path path, long size, String contentType)
        implements FileUpload {

    public static FileUpload of(FileUpload upload) {
        String fileName = upload.fileName();
        long size = upload.size();
        String contentType = upload.contentType();
        Path source = upload.uploadedFile();
        try {
            Path detached = source.resolveSibling(source.getFileName() + "-import");
            Files.move(source, detached, StandardCopyOption.REPLACE_EXISTING);
            return new DetachedUpload(fileName, detached, size, contentType);
        } catch (IOException e) {
            Log.warnf(e, "Could not detach upload %s from its request", fileName);
            return upload;
        }
    }

    public static void release(FileUpload upload) {
        if (!(upload instanceof DetachedUpload detached)) {
            return;
        }
        try {
            Files.deleteIfExists(detached.path);
        } catch (IOException e) {
            Log.warnf(e, "Could not delete detached upload %s", detached.path);
        }
    }

    @Override
    public Path uploadedFile() {
        return path;
    }

    @Override
    public Path filePath() {
        return path;
    }

    @Override
    public String name() {
        return "file";
    }

    @Override
    public String charSet() {
        return null;
    }

    @Override
    public MultivaluedMap<String, String> getHeaders() {
        return new MultivaluedHashMap<>();
    }
}
