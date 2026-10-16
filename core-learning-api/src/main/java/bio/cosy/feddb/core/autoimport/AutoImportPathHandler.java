package bio.cosy.feddb.core.autoimport;

import bio.cosy.feddb.core.helper.ResourceScanner;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.Strings;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;


@ApplicationScoped
public class AutoImportPathHandler {

    private static final String JSON_EXTENSION = ".json";
    private static final Path DEV_RESOURCES_ROOT = Path.of("src", "main", "resources");

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ResourceScanner resourceScanner;

    private ObjectMapper nonNullMapper;

    @PostConstruct
    void init() {
        nonNullMapper = objectMapper.copy().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    public <T> List<T> loadAll(String name, FLNetConfigPaths config, Class<T> type) {
        return loadAll(name, config, type, OnParseError.SKIP);
    }

    public <T> List<T> loadAll(String name, FLNetConfigPaths config, Class<T> type, OnParseError onParseError) {
        return readAll(name, resolveSources(name, config), type, onParseError);
    }

    public List<PathOrUrl> resolveSources(String name, FLNetConfigPaths config) {
        if (!config.enabled()) {
            return List.of();
        }

        List<Path> roots = config.paths().orElse(List.of());
        if (roots.isEmpty()) {
            Log.warnf("%s import enabled but no paths configured", name);
            return List.of();
        }

        List<PathOrUrl> sources = roots.stream()
                .flatMap(this::resolveSources)
                .sorted(Comparator.comparing(PathOrUrl::sortKey))
                .toList();
        List<PathOrUrl> whitelisted = PathOrUrl.filterByWhitelist(sources, config.whiteList());
        Log.infof("%s import found %d source(s)", name, whitelisted.size());
        return whitelisted;
    }

    public Stream<PathOrUrl> resolveSources(Path root) {
        if (root == null) {
            return Stream.empty();
        }
        if (isHttpUrl(root)) {
            return resolveHttpSource(root);
        }
        if (root.isAbsolute()) {
            return resourceScanner.listFiles(root, JSON_EXTENSION).stream().map(PathOrUrl::ofPath);
        }
        return resourceScanner.listClasspathFiles(root, JSON_EXTENSION).stream().map(PathOrUrl::ofUrl);
    }

    public <T> List<T> readAll(String name, List<PathOrUrl> sources, Class<T> type) {
        return readAll(name, sources, type, OnParseError.SKIP);
    }

    public <T> List<T> readAll(String name, List<PathOrUrl> sources, Class<T> type, OnParseError onParseError) {
        return sources.stream()
                .flatMap(src -> read(name, src, type, onParseError).stream())
                .toList();
    }

    public <T> List<T> read(String name, PathOrUrl src, Class<T> type, OnParseError onParseError) {
        try {
            return read(src, type);
        } catch (Exception e) {
            if (onParseError == OnParseError.FAIL) {
                throw new IllegalStateException("Failed to parse " + name + " import source as " + type.getSimpleName() + ": " + src, e);
            }
            Log.errorf("Failed to parse %s import source as %s: %s, error: %s", name, type.getSimpleName(), src, e.getMessage());
            return List.of();
        }
    }

    private <T> List<T> read(PathOrUrl src, Class<T> type) throws IOException {
        JsonNode root = src.readTree(objectMapper);
        if (root == null || root.isNull()) {
            return List.of();
        }
        List<T> values = objectMapper.readerForListOf(type)
                .with(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .readValue(root);
        return values.stream().filter(Objects::nonNull).toList();
    }


    public ObjectNode toJsonTree(Object value) {
        return nonNullMapper.valueToTree(value);
    }


    public Optional<Path> writeJson(String name, FLNetConfigPaths config, String fileName, Object content) {
        Optional<Path> root = resolveWritableRoot(name, config);
        if (root.isEmpty()) {
            Log.warnf("No writable %s export root could be resolved; not saving %s", name, fileName);
            return Optional.empty();
        }

        Path file = root.get().resolve(fileName).normalize();
        if (!file.startsWith(root.get())) {
            Log.warnf("Refusing to write %s outside of %s export root %s", fileName, name, root.get());
            return Optional.empty();
        }
        return writeJson(file, content);
    }

    public Optional<Path> writeJson(Path file, Object content) {
        try {
            PathUtils.createParentDirectories(file);
            nonNullMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), content);
            Log.infof("Saved auto-import JSON: %s", file);
            return Optional.of(file);
        } catch (Exception e) {
            Log.warnf(e, "Failed to serialize/write auto-import JSON to %s", file);
            return Optional.empty();
        }
    }

    public Optional<Path> resolveWritableRoot(String name, FLNetConfigPaths config) {
        List<Path> roots = config.paths().orElse(List.of());
        if (roots.isEmpty()) {
            Log.warnf("%s export requested but no paths configured", name);
            return Optional.empty();
        }

        for (Path root : roots) {
            if (root == null || isHttpUrl(root)) {
                continue;
            }
            if (!root.isAbsolute() && LaunchMode.current() != LaunchMode.DEVELOPMENT) {
                Log.warnf("Relative %s export paths are only supported in development mode, skipping: %s", name, root);
                continue;
            }

            Path target = root.isAbsolute() ? root : DEV_RESOURCES_ROOT.resolve(root);
            try {
                Files.createDirectories(target);
                if (Files.isWritable(target)) {
                    return Optional.of(target);
                }
                Log.warnf("%s export root not writable: %s", name, target);
            } catch (IOException e) {
                Log.warnf(e, "Failed to prepare %s export root: %s", name, target);
            }
        }
        return Optional.empty();
    }

    private boolean isHttpUrl(Path root) {
        // Path collapses "//", so "https://host" arrives as "https:/host"
        return Strings.CI.startsWithAny(root.toString(), "http:/", "https:/");
    }

    private Stream<PathOrUrl> resolveHttpSource(Path root) {
        String value = Strings.CS.replaceOnce(root.toString(), ":/", "://");
        try {
            return Stream.of(PathOrUrl.ofExternalUrl(URI.create(value).toURL()));
        } catch (Exception e) {
            Log.warnf(e, "Invalid auto-import URL: %s, skipping", root);
            return Stream.empty();
        }
    }
}
