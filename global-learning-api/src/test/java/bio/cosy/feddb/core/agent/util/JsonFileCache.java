package bio.cosy.feddb.core.agent.util;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.quarkus.logging.Log;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JsonFileCache {

    private static final ObjectMapper OM = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private JsonFileCache() {
    }

    public static <T> List<T> loadList(Path path, TypeReference<List<T>> typeReference) {
        try {
            if (!Files.exists(path)) {
                return new ArrayList<>();
            }
            return OM.readValue(path.toFile(), typeReference);
        } catch (JacksonException e) {
            Log.errorf(e, "Failed to read cache file %s", path);
            throw new RuntimeException("Failed to read cache file " + path, e);
        }
    }

    public static void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            OM.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
        } catch (JacksonException | IOException e) {
            Log.errorf(e, "Failed to write cache file %s", path);
            throw new RuntimeException("Failed to write cache file " + path, e);
        }
    }

    public static <T> Optional<T> findFirst(List<T> items, java.util.function.Predicate<T> predicate) {
        return items.stream().filter(predicate).findFirst();
    }
}
