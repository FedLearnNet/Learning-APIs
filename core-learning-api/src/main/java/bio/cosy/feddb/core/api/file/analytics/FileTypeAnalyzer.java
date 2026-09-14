package bio.cosy.feddb.core.api.file.analytics;

import bio.cosy.feddb.core.api.app.config.ToolConfigDataType;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class FileTypeAnalyzer {

    private static final Tika TIKA = new Tika();

    public static String analyseContentType(File file, String origFileName) throws IOException {
        if (file == null) {
            return "application/octet-stream";
        }
        return analyseContentType(file.toPath(), origFileName);
    }

    public static String analyseContentType(Path path, String origFileName) throws IOException {
        Metadata metadata = metadata(origFileName);
        String type;
        try (TikaInputStream in = TikaInputStream.get(path)) {
            type = TIKA.detect(in, metadata);
        }
        return normalizeContentType(type, origFileName);
    }

    public static String analyseContentType(InputStream in, String origFileName) throws IOException {
        String type = TIKA.detect(in, metadata(origFileName));
        return normalizeContentType(type, origFileName);
    }

    public static ToolConfigDataType analyseDataType(InputStream in, String origFileName) {
        try {
            InputStream markable = in.markSupported() ? in : new BufferedInputStream(in);
            return ToolConfigDataType.fromMimeType(analyseContentType(markable, origFileName));
        } catch (IOException e) {
            return ToolConfigDataType.UNKNOWN;
        }
    }

    public static String analyseContentType(InputStream in) throws IOException {
        return normalizeContentType(TIKA.detect(in), null);
    }

    private static Metadata metadata(String origFileName) {
        Metadata metadata = new Metadata();
        if (origFileName != null && !origFileName.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, origFileName);
        }
        return metadata;
    }

    private static String normalizeContentType(String type, String origFileName) {
        if (type == null || type.isBlank()) {
            return "application/octet-stream";
        }
        if ("text/plain".equals(type)) {
            FileDataType dataType = FileDataType.fromFilename(origFileName);
            if (dataType != FileDataType.UNKNOWN) {
                return dataType.getMineType();
            }
        }
        return type;
    }
}
