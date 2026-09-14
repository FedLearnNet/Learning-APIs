package bio.cosy.feddb.core.agent.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonLoader {

    private static final ObjectMapper OM = new ObjectMapper().findAndRegisterModules();

    private JsonLoader() {
    }

    public static <T> T loadJson(String path, TypeReference<T> typeReference) {
        try {
            Path file = Path.of(path);
            if (Files.exists(file)) {
                return OM.readValue(file.toFile(), typeReference);
            }

            try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
                if (is == null) {
                    throw new IllegalArgumentException("JSON resource not found: " + path);
                }
                return OM.readValue(is, typeReference);
            }
        } catch (IOException e) {
            Log.errorf(e, "Failed to load json from %s", path);
            throw new RuntimeException("Failed to load json from " + path, e);
        }
    }
}
