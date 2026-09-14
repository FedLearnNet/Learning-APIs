package de.unihamburg.daibetes.api.app;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.model.ModelDetailDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.unihamburg.daibetes.api.model.ModelBO;
import de.unihamburg.daibetes.api.workflow.export.WorkflowExportDTO;
import de.unihamburg.daibetes.config.FLNetConfig;
import de.unihamburg.daibetes.helper.PathOrUrl;
import de.unihamburg.daibetes.helper.ToolExternalCleaner;
import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@ApplicationScoped
public class ToolExternalHandler {

    @Inject
    FLNetConfig flnet;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    FederatedAppBO appBO;

    @Inject
    ModelBO modelBO;

    @Transactional
    public void importAllToolsTransactional() {
        if (!flnet.toolImports().enabled()) {
            return;
        }

        if (flnet.toolImports().paths().isEmpty()) {
            Log.warnf("Tool import enabled but no paths configured");
            return;
        }

        List<Path> roots = flnet.toolImports().paths().get();
        if (roots.isEmpty()) {
            Log.warnf("Tool import enabled but no paths configured");
            return;
        }

        List<PathOrUrl> jsonSources = roots.stream().flatMap(this::resolveJsonSources).sorted(Comparator.comparing(PathOrUrl::sortKey)).toList();

        List<FederatedAppDetailDTO> tools = loadAllApps(jsonSources);
        for (FederatedAppDetailDTO tool : tools) {
            try {
                appBO.createExternal(tool, flnet.toolImports().createUnpublishedVersion(), flnet.toolImports().overrideExisting());
            } catch (Exception e) {
                Log.warnf(e, "Failed to import tool from source: %s", tool.getName());
            }
        }

        List<ModelDetailDTO> models = loadAllModels(jsonSources);
        for (ModelDetailDTO model : models) {
            if (model.getFederatedApp() == null) {
                // is Tool not an app;
                continue;
            }
            try {
                appBO.createExternal(model.getFederatedApp(), flnet.toolImports().createUnpublishedVersion(), flnet.toolImports().overrideExisting());
                modelBO.createExternal(model);
            } catch (Exception e) {
                Log.warnf(e, "Failed to import model from source: %s", model.getName());
            }
        }

    }

    public List<FederatedAppDetailDTO> loadAllApps(List<PathOrUrl> jsonSources) {
        return jsonSources.stream().flatMap(src -> parseAppSource(src).stream()).filter(Objects::nonNull).toList();
    }

    public List<ModelDetailDTO> loadAllModels(List<PathOrUrl> jsonSources) {
        return jsonSources.stream().flatMap(src -> parseModelSource(src).stream()).filter(Objects::nonNull).toList();
    }

    public List<WorkflowExportDTO> loadAllWorkflows(List<PathOrUrl> jsonSources) {
        return jsonSources.stream().flatMap(src -> parseWorkflowSource(src).stream()).filter(Objects::nonNull).toList();
    }

    public Stream<PathOrUrl> resolveJsonSources(Path root) {
        if (root == null) return Stream.empty();

        // Check if the path string starts with "http" (external URL)
        String rootStr = root.toString();
        if (rootStr.startsWith("http:/") || rootStr.startsWith("https:/")) {
            // Normalize single-slash URLs to double-slash
            String normalizedStr = rootStr;
            if (rootStr.startsWith("http:/") && !rootStr.startsWith("http://")) {
                normalizedStr = rootStr.replaceFirst("http:/", "http://");
            } else if (rootStr.startsWith("https:/") && !rootStr.startsWith("https://")) {
                normalizedStr = rootStr.replaceFirst("https:/", "https://");
            }

            try {
                URL url = URI.create(normalizedStr).toURL();
                return Stream.of(PathOrUrl.ofExternalUrl(url));
            } catch (Exception e) {
                Log.warnf(e, "Invalid URL: %s, skipping this config", rootStr);
                return Stream.empty();
            }
        }

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
                return stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).toList().stream();
            }
        } catch (IOException e) {
            Log.warnf(e, "Failed to scan tool-config folder: %s", dir);
            return Stream.empty();
        }
    }

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

        return out.stream();
    }

    private List<PathOrUrl> listJsonUnderResourceUrl(URL folderUrl) {
        try {
            URI uri = folderUrl.toURI();
            String scheme = uri.getScheme();

            // file:/.../target/classes/folder
            if ("file".equalsIgnoreCase(scheme)) {
                Path p = Paths.get(uri);
                try (Stream<Path> s = Files.walk(p)) {
                    return s.filter(Files::isRegularFile).filter(x -> x.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).map(PathOrUrl::ofPath).toList();
                }
            }

            // jar:file:/.../app.jar!/tool-imports
            if ("jar".equalsIgnoreCase(scheme)) {
                FileSystem fs = null;
                boolean created = false;
                try {
                    try {
                        fs = FileSystems.getFileSystem(uri);
                    } catch (FileSystemNotFoundException e) {
                        // Needed when running from a packaged JAR
                        fs = FileSystems.newFileSystem(uri, Map.of());
                        created = true;
                    }

                    Path p = Paths.get(uri);
                    try (Stream<Path> s = Files.walk(p)) {
                        return s.filter(Files::isRegularFile).filter(x -> x.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).map(PathOrUrl::ofPath).toList();
                    }
                } finally {
                    // Only close if we created it; otherwise we might break other callers.
                    if (created && fs != null) {
                        try {
                            fs.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            // Fallback: read directly by URL (not recursive)
            Log.warnf("Unsupported resource URI scheme '%s' for %s (will treat as single file if *.json)", scheme, uri);
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

    private List<FederatedAppDetailDTO> parseAppSource(PathOrUrl src) {
        try {
            JsonNode root = src.readTree(objectMapper);
            if (root == null || root.isNull()) return List.of();

            if (root.isArray()) {
                List<FederatedAppDetailDTO> list = new ArrayList<>();
                for (JsonNode n : root) {
                    if (n != null && n.isObject()) {
                        list.add(objectMapper.treeToValue(n, FederatedAppDetailDTO.class));
                    } else if (n != null && !n.isNull()) {
                        throw new IllegalArgumentException("Expected object elements in array: " + src);
                    }
                }
                return list;
            }

            if (root.isObject()) {
                FederatedAppDetailDTO one = objectMapper.treeToValue(root, FederatedAppDetailDTO.class);
                return List.of(one);
            }

            Log.errorf("Unexpected JSON root type in tool-config source: %s, expected array or object", src);

        } catch (Exception e) {
            Log.errorf("Failed to parse tool-config source: %s, error: %s", src, e.getMessage());
        }
        return List.of();
    }

    private List<WorkflowExportDTO> parseWorkflowSource(PathOrUrl src) {
        try {
            JsonNode root = src.readTree(objectMapper);
            if (root == null || root.isNull()) return List.of();

            if (root.isArray()) {
                List<WorkflowExportDTO> list = new ArrayList<>();
                for (JsonNode n : root) {
                    if (n != null && n.isObject()) {
                        list.add(objectMapper.treeToValue(n, WorkflowExportDTO.class));
                    } else if (n != null && !n.isNull()) {
                        throw new IllegalArgumentException("Expected object elements in array: " + src);
                    }
                }
                return list;
            }

            if (root.isObject()) {
                WorkflowExportDTO one = objectMapper.treeToValue(root, WorkflowExportDTO.class);
                return List.of(one);
            }

            Log.errorf("Unexpected JSON root type in workflow source: %s, expected array or object", src);

        } catch (Exception e) {
            Log.errorf("Failed to parse workflow source: %s, error: %s", src, e.getMessage());
        }
        return List.of();
    }

    private List<ModelDetailDTO> parseModelSource(PathOrUrl src) {
        try {
            JsonNode root = src.readTree(objectMapper);
            if (root == null || root.isNull()) return List.of();

            if (root.isArray()) {
                List<ModelDetailDTO> list = new ArrayList<>();
                for (JsonNode n : root) {
                    if (n != null && n.isObject()) {
                        list.add(objectMapper.treeToValue(n, ModelDetailDTO.class));
                    } else if (n != null && !n.isNull()) {
                        throw new IllegalArgumentException("Expected object elements in array: " + src);
                    }
                }
                return list;
            }

            if (root.isObject()) {
                ModelDetailDTO one = objectMapper.treeToValue(root, ModelDetailDTO.class);
                return List.of(one);
            }

            throw new IllegalArgumentException("JSON root must be array or object: " + src);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse tool-config source: " + src, e);
        }
    }


    private static String normalizeResourceFolder(Path relative) {
        String s = relative.toString().replace('\\', '/');
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }

    public Optional<Path> saveToolAsJson(FederatedAppDetailDTO tool) {
        if (tool == null) {
            Log.warn("saveToolAsJson called with null tool");
            return Optional.empty();
        }

        Path outRoot = resolveWritableToolExportRoot(flnet.toolImports());
        if (outRoot == null) {
            Log.warn("No writable tool export root could be resolved; not saving tool JSON");
            return Optional.empty();
        }

        ToolExternalCleaner.cleanup(tool);
        String slug = safeSlug(tool);

        Path outFile = outRoot.resolve(slug + ".json");

        ObjectMapper writer = objectMapper.copy().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

        try {
            Files.createDirectories(outRoot);
            String json = writer.writerWithDefaultPrettyPrinter().writeValueAsString(tool);
            Files.writeString(outFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Log.infof("Saved tool JSON: %s -> %s", slug, outFile);
            return Optional.of(outFile);
        } catch (Exception e) {
            Log.warnf(e, "Failed to serialize/write tool JSON for %s to %s", slug, outFile);
            return Optional.empty();
        }
    }

    public Optional<Path> replaceBuildInfoInJson(FederatedAppDetailDTO tool) {
        Path outRoot = resolveWritableToolExportRoot(flnet.toolImports());
        Path outFile = outRoot == null ? null : outRoot.resolve(safeSlug(tool) + ".json");
        if (outFile == null || !Files.exists(outFile)) return saveToolAsJson(tool);
        try {
            JsonNode root = objectMapper.readTree(outFile.toFile());
            JsonNode version = root.path("versions").path(0);
            if (!(version instanceof ObjectNode target) || tool.getPublishInfo() == null || tool.getImageName() == null) return Optional.empty();
            target.set("publishInfo", objectMapper.valueToTree(tool.getPublishInfo()));
            target.put("imageName", tool.getImageName());
            Files.writeString(outFile, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardOpenOption.TRUNCATE_EXISTING);
            return Optional.of(outFile);
        } catch (Exception e) {
            Log.warnf(e, "Failed to replace build information in %s", outFile);
            return Optional.empty();
        }
    }

    public Optional<Path> saveWorkflowAsJson(WorkflowExportDTO workflow, String fileName) {
        if (workflow == null) {
            Log.warn("saveToolAsJson called with null tool");
            return Optional.empty();
        }

        Path outRoot = resolveWritableToolExportRoot(flnet.workflowImports());
        if (outRoot == null) {
            Log.warn("No writable tool export root could be resolved; not saving tool JSON");
            return Optional.empty();
        }

        Path outFile = outRoot.resolve(fileName);

        ObjectMapper writer = objectMapper.copy().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

        try {
            Files.createDirectories(outRoot);
            String json = writer.writerWithDefaultPrettyPrinter().writeValueAsString(workflow);
            Files.writeString(outFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Log.infof("Saved tool JSON: %s -> %s", fileName, outFile);
            return Optional.of(outFile);
        } catch (Exception e) {
            Log.warnf(e, "Failed to serialize/write tool JSON for %s to %s", fileName, outFile);
            return Optional.empty();
        }
    }


    private Path resolveWritableToolExportRoot(FLNetConfig.ToolImportConfig config) {
        try {
            if (config.paths().isEmpty()) {
                Log.warnf("Tool export requested but no paths configured");
                return null;
            }
            List<Path> roots = config.paths().get();
            for (Path p : roots) {
                if (p == null) continue;
                String s = p.toString();
                if (s.startsWith("http:/") || s.startsWith("https:/")) continue;
                if (p.isAbsolute()) {
                    try {
                        Files.createDirectories(p);
                        if (Files.isDirectory(p) && Files.isWritable(p)) {
                            return p;
                        }
                        Log.warnf("Configured export root not writable: %s", p);
                    } catch (IOException e) {
                        Log.warnf(e, "Failed to prepare configured export root: %s", p);
                    }
                } else {
                    if (LaunchMode.current() != LaunchMode.DEVELOPMENT) {
                        Log.warnf("Relative export paths are only supported in development mode, skipping configured export root: %s", p);
                        continue;
                    }
                    ClassLoader cl = Thread.currentThread().getContextClassLoader();
                    try {
                        Path out = Path.of("src/main/resources" + FileSystems.getDefault().getSeparator() + normalizeResourceFolder(p));

                        Files.createDirectories(out);
                        if (Files.isDirectory(out) && Files.isWritable(out)) {
                            return out;
                        }
                        Log.warnf("Resolved export root not writable: %s", out);
                    } catch (Exception e) {
                        Log.warnf(e, "Failed to prepare resolved export root: %s", p);
                    }
                }
            }
        } catch (Exception e) {
            Log.warnf(e, "Failed to resolve tool export root from config");
        }
        return null;
    }

    private String safeSlug(FederatedAppDetailDTO dto) {
        if (dto == null) return null;
        String fallback = "tool_" + (dto.getUniqueAppId() != null ? dto.getUniqueAppId() : Math.abs(Objects.hash(dto)));
        if (dto.getSlug() == null) return fallback;
        String s = dto.getSlug().trim().toLowerCase(Locale.ROOT);
        return s.isBlank() ? fallback : s;
    }

}
