package bio.cosy.feddb.core.agent.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JsonFileCache {

    private static final ObjectMapper OM = new ObjectMapper().findAndRegisterModules();

    private JsonFileCache() {
    }

    public static <T> List<T> loadList(Path path, TypeReference<List<T>> typeReference) {
        try {
            if (!Files.exists(path)) {
                return new ArrayList<>();
            }
            return OM.readValue(path.toFile(), typeReference);
        } catch (IOException e) {
            Log.errorf(e, "Failed to read cache file %s", path);
            throw new RuntimeException("Failed to read cache file " + path, e);
        }
    }

    public static void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            OM.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
        } catch (IOException e) {
            Log.errorf(e, "Failed to write cache file %s", path);
            throw new RuntimeException("Failed to write cache file " + path, e);
        }
    }

    public static <T> Optional<T> findFirst(List<T> items, java.util.function.Predicate<T> predicate) {
        return items.stream().filter(predicate).findFirst();
    }
}
