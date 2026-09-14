package de.unihamburg.daibetes.api.app.config;

import bio.cosy.feddb.core.api.app.config.ToolConfigDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.config.FLNetConfig;
import de.unihamburg.daibetes.helper.PathOrUrl;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@ApplicationScoped
public class ToolConfigExternalStandardHandler {

    @Inject
    FLNetConfig flnet;

    @Inject
    ObjectMapper objectMapper;

    public List<ToolConfigDTO> loadAll() {
        if (!flnet.toolConfigs().enabled()) {
            return List.of();
        }

        if (flnet.toolConfigs().paths().isEmpty()) {
            return List.of();
        }

        List<Path> roots = flnet.toolConfigs().paths().get();
        if (roots.isEmpty()) {
            return List.of();
        }

        List<PathOrUrl> jsonSources = roots.stream()
                .flatMap(this::resolveJsonSources)
                .sorted(Comparator.comparing(PathOrUrl::sortKey))
                .toList();

        List<ToolConfigDTO> out = new ArrayList<>();
        for (PathOrUrl src : jsonSources) {
            out.addAll(parseToolConfigSource(src));
        }
        return out;
    }

    private Stream<PathOrUrl> resolveJsonSources(Path root) {
        if (root == null) return Stream.empty();

        if (root.isAbsolute()) {
            return listJsonFilesFromFs(root).map(PathOrUrl::ofPath);
        }

        String resourceFolder = normalizeResourceFolder(root);
        return listJsonFilesFromResources(resourceFolder);
    }

    private Stream<Path> listJsonFilesFromFs(Path dir) {
        try {
            if (!Files.exists(dir)) {
                Log.warnf("Tool-config folder does not exist: %s", dir);
                return Stream.empty();
            }
            if (!Files.isDirectory(dir)) {
                Log.warnf("Tool-config path is not a directory: %s", dir);
                return Stream.empty();
            }

            try (Stream<Path> stream = Files.walk(dir)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .toList()
                        .stream();
            }
        } catch (IOException e) {
            Log.warnf(e, "Failed to scan tool-config folder: %s", dir);
            return Stream.empty();
        }
    }

    /**
     * Lists *.json inside a resource folder for both:
     * - file: URLs (dev mode)
     * - jar: URLs (packaged)
     */
    private Stream<PathOrUrl> listJsonFilesFromResources(String resourceFolder) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        List<PathOrUrl> out = new ArrayList<>();
        try {
            Enumeration<URL> roots = cl.getResources(resourceFolder);
            while (roots.hasMoreElements()) {
                URL url = roots.nextElement();
                out.addAll(listJsonUnderResourceUrl(url));
            }
        } catch (IOException e) {
            Log.warnf(e, "Failed to locate resource folder: %s", resourceFolder);
        }

        // If folder itself isn't a "directory resource" (common), also try to resolve by walking parent
        // (Most setups with getResources(folder) are fine for file:/... and jar:/... if folder exists)

        return out.stream();
    }

    private List<PathOrUrl> listJsonUnderResourceUrl(URL folderUrl) {
        try {
            URI uri = folderUrl.toURI();

            // file:/.../target/classes/folder
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                Path p = Paths.get(uri);
                try (Stream<Path> s = Files.walk(p)) {
                    return s.filter(Files::isRegularFile)
                            .filter(x -> x.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                            .map(PathOrUrl::ofPath)
                            .toList();
                }
            }

            // Fallback: read directly by URL (not recursive)
            Log.warnf("Unsupported resource URI scheme '%s' for %s (will treat as single file if *.json)", uri.getScheme(), uri);
            String path = uri.toString().toLowerCase(Locale.ROOT);
            if (path.endsWith(".json")) {
                return List.of(PathOrUrl.ofUrl(folderUrl));
            }
            return List.of();

        } catch (URISyntaxException | IOException e) {
            Log.warnf(e, "Failed to walk resource folder URL: %s", folderUrl);
            return List.of();
        }
    }

    private List<ToolConfigDTO> parseToolConfigSource(PathOrUrl src) {
        try {
            JsonNode root = src.readTree(objectMapper);
            if (root == null || root.isNull()) return List.of();

            if (root.isArray()) {
                List<ToolConfigDTO> list = new ArrayList<>();
                for (JsonNode n : root) {
                    if (n != null && n.isObject()) {
                        list.add(objectMapper.treeToValue(n, ToolConfigDTO.class));
                    } else if (n != null && !n.isNull()) {
                        throw new IllegalArgumentException("Expected object elements in array: " + src);
                    }
                }
                return list;
            }

            if (root.isObject()) {
                ToolConfigDTO one = objectMapper.treeToValue(root, ToolConfigDTO.class);
                return List.of(one);
            }

            throw new IllegalArgumentException("JSON root must be array or object: " + src);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse tool-config source: " + src, e);
        }
    }

    private static String normalizeResourceFolder(Path relative) {
        // Path like "tool-configs" or "tool-configs/domain"
        String s = relative.toString().replace('\\', '/');
        while (s.startsWith("/")) s = s.substring(1);
        // Do NOT force trailing slash; getResources works with folder name as-is
        return s;
    }
}
