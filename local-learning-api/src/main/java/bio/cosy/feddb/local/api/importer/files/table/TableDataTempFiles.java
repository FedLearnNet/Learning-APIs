package bio.cosy.feddb.local.api.importer.files.table;

import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Where the ETL puts what it spills to disk.
 *
 * <p>Reading, merging, pivoting and grouping all write temporary files, and an installation that
 * points the ETL at a volume of its own expects every one of them to land there. Asking one place
 * keeps that true when a step is added.</p>
 */
public final class TableDataTempFiles {

    private TableDataTempFiles() {
    }

    /** The configured base directory, or {@code null} to use the JVM's temp directory. */
    public static Path baseDirectory(FLNetClientConfig config) {
        return config == null ? null : config.connector().etlWorkDirectory()
                .filter(directory -> !directory.isBlank())
                .map(Path::of)
                .orElse(null);
    }

    /** A fresh directory for one step's temporary files. */
    public static Path createWorkDirectory(FLNetClientConfig config, String prefix) throws IOException {
        Path base = baseDirectory(config);
        if (base == null) {
            return Files.createTempDirectory(prefix);
        }
        Files.createDirectories(base);
        return Files.createTempDirectory(base, prefix);
    }

    static void deleteRecursively(Path path) {
        if (path == null) {
            return;
        }

        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(tempPath -> {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException e) {
                    Log.debugf(e, "Could not delete table-data temp path %s", tempPath);
                }
            });
        } catch (IOException e) {
            Log.debugf(e, "Could not clean up table-data temp directory %s", path);
        }
    }
}
