package bio.cosy.feddb.core.helper;

import io.quarkus.logging.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Shared helper for loading textual resources identified by a path/URL spec.
 * <p>
 * Resolution order:
 * <ol>
 *   <li>Classpath (strips a leading slash)</li>
 *   <li>URI with scheme ({@code http}, {@code https}, {@code file}, ...)</li>
 *   <li>Filesystem path</li>
 * </ol>
 * The classpath lookup means tests and dev mode can ship example files under
 * {@code src/main/resources/...} and reference them via the same spec string
 * (e.g. {@code /connectors/us-130-clinics.json}) that would otherwise point at a real file.
 */
public final class ResourceLoader {

    private ResourceLoader() {
    }

    /**
     * Load the given spec as a UTF-8 string, trying classpath first, then URL, then filesystem.
     *
     * @throws IOException if none of the resolution strategies find the resource
     */
    public static String loadAsString(String spec) throws IOException {
        if (spec == null || spec.isBlank()) {
            throw new IOException("Resource spec is empty");
        }
        Log.debugf("ResourceLoader: resolving %s", spec);

        // 1. classpath
        String cp = spec.startsWith("/") ? spec.substring(1) : spec;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ResourceLoader.class.getClassLoader();
        }
        URL resource = cl.getResource(cp);
        if (resource != null) {
            try (InputStream in = resource.openStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        // 2. URI with scheme
        try {
            URI uri = URI.create(spec.trim());
            String scheme = uri.getScheme();
            if (scheme != null) {
                if ("file".equalsIgnoreCase(scheme)) {
                    return Files.readString(Path.of(uri), StandardCharsets.UTF_8);
                }
                URLConnection connection = uri.toURL().openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(10_000);
                try (InputStream in = connection.getInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to filesystem
        }

        // 3. filesystem path
        Path path = Path.of(spec);
        if (Files.isRegularFile(path)) {
            return Files.readString(path, StandardCharsets.UTF_8);
        }

        throw new IOException("Resource not found on classpath, URL, or filesystem: " + spec);
    }

    public static File loadAsFile(String spec) throws IOException {
        if (spec == null || spec.isBlank()) {
            throw new IOException("Resource spec is empty");
        }

        Log.debugf("ResourceLoader: resolving %s", spec);

        // 1. classpath
        String cp = spec.startsWith("/") ? spec.substring(1) : spec;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ResourceLoader.class.getClassLoader();
        }

        URL resource = cl.getResource(cp);
        if (resource != null) {
            try (InputStream in = resource.openStream()) {
                return copyToTempFile(in, extractFileName(cp));
            }
        }

        // 2. URI with scheme
        try {
            URI uri = URI.create(spec.trim());
            String scheme = uri.getScheme();
            if (scheme != null) {
                if ("file".equalsIgnoreCase(scheme)) {
                    Path path = Path.of(uri);
                    if (Files.isRegularFile(path)) {
                        return path.toFile();
                    }
                } else {
                    URLConnection connection = uri.toURL().openConnection();
                    connection.setConnectTimeout(10_000);
                    connection.setReadTimeout(10_000);
                    try (InputStream in = connection.getInputStream()) {
                        return copyToTempFile(in, extractFileName(spec));
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to filesystem
        }

        // 3. filesystem path
        Path path = Path.of(spec);
        if (Files.isRegularFile(path)) {
            return path.toFile();
        }

        throw new IOException("Resource not found on classpath, URL, or filesystem: " + spec);
    }

    private static File copyToTempFile(InputStream in, String originalName) throws IOException {
        String safeName = (originalName == null || originalName.isBlank()) ? "resource.tmp" : originalName;
        String prefix = extractPrefix(safeName);
        String suffix = extractSuffix(safeName);

        Path tempFile = Files.createTempFile(prefix, suffix);
        Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        tempFile.toFile().deleteOnExit();
        return tempFile.toFile();
    }

    private static String extractFileName(String spec) {
        String normalized = spec.replace('\\', '/');
        int idx = normalized.lastIndexOf('/');
        return idx >= 0 ? normalized.substring(idx + 1) : normalized;
    }

    private static String extractPrefix(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        base = base.replaceAll("[^a-zA-Z0-9-_]", "_");
        if (base.length() < 3) {
            base = (base + "___").substring(0, 3);
        }
        return base;
    }

    private static String extractSuffix(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            return fileName.substring(dot);
        }
        return ".tmp";
    }

}
