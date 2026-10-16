package bio.cosy.feddb.core.helper;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.file.PathFilter;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.Strings;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@ApplicationScoped
public class ResourceScanner {

    public List<Path> listFiles(Path dir, String extension) {
        if (!Files.isDirectory(dir)) {
            Log.warnf("Folder does not exist or is not a directory: %s", dir);
            return List.of();
        }
        try {
            return walk(dir, extension);
        } catch (IOException e) {
            Log.warnf(e, "Failed to scan folder: %s", dir);
            return List.of();
        }
    }

    public List<URL> listClasspathFiles(Path folder, String extension) {
        String resourceName = FilenameUtils.separatorsToUnix(folder.toString());
        List<URL> out = new ArrayList<>();
        try {
            for (URL folderUrl : Collections.list(classLoader().getResources(resourceName))) {
                out.addAll(listFilesUnderUrl(folderUrl, extension));
            }
        } catch (IOException e) {
            Log.warnf(e, "Failed to locate classpath folder: %s", resourceName);
        }
        return out;
    }

    private List<URL> listFilesUnderUrl(URL folderUrl, String extension) {
        try {
            URI uri = folderUrl.toURI();
            return switch (uri.getScheme().toLowerCase(Locale.ROOT)) {
                case "file" -> toUrls(walk(Path.of(uri), extension));
                case "jar" -> listFilesInJar(uri, extension);
                default -> {
                    Log.warnf("Unsupported resource URI scheme '%s' for %s, treating it as a single file", uri.getScheme(), uri);
                    yield Strings.CI.endsWith(folderUrl.getPath(), extension) ? List.of(folderUrl) : List.of();
                }
            };
        } catch (URISyntaxException | IOException e) {
            Log.warnf(e, "Failed to walk classpath folder: %s", folderUrl);
            return List.of();
        }
    }

    private List<URL> listFilesInJar(URI folderUri, String extension) throws IOException {
        // Only close a file system we opened ourselves; a shared one may still be in use elsewhere
        try (FileSystem ignored = FileSystems.newFileSystem(folderUri, Map.of())) {
            return toUrls(walk(Path.of(folderUri), extension));
        } catch (FileSystemAlreadyExistsException e) {
            return toUrls(walk(Path.of(folderUri), extension));
        }
    }

    private List<Path> walk(Path dir, String extension) throws IOException {
        PathFilter filter = FileFileFilter.INSTANCE.and(new SuffixFileFilter(extension, IOCase.INSENSITIVE));
        try (Stream<Path> files = PathUtils.walk(dir, filter, Integer.MAX_VALUE, false)) {
            return files.toList();
        }
    }

    private List<URL> toUrls(List<Path> paths) throws IOException {
        List<URL> urls = new ArrayList<>(paths.size());
        for (Path path : paths) {
            urls.add(path.toUri().toURL());
        }
        return urls;
    }

    private ClassLoader classLoader() {
        return ObjectUtils.getIfNull(Thread.currentThread().getContextClassLoader(), ResourceScanner.class::getClassLoader);
    }
}
