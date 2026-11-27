package bio.cosy.feddb.core.agent.util;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.quarkus.logging.Log;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonLoader {

    private static final ObjectMapper OM = JsonMapper.builder()
            .findAndAddModules()
            .build();

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
        } catch (JacksonException | IOException e) {
            Log.errorf(e, "Failed to load json from %s", path);
            throw new RuntimeException("Failed to load json from " + path, e);
        }
    }
}
