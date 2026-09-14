package bio.cosy.feddb.local.api.importer;

import bio.cosy.feddb.local.api.file.FileBO;
import bio.cosy.feddb.local.api.file.FileEntity;
import bio.cosy.feddb.local.api.importer.connector.ConnectorBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunBO;
import io.quarkus.logging.Log;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches local etl-data-path files configured per auto-subscribe group.
 * On change, the file is re-uploaded via {@link FileBO}, the connector input
 * is rewired to the new file id, and a comprehensive run is triggered.
 */
@ApplicationScoped
public class ImporterDataFileWatcherBO {

    /** Debounce window for filesystem events — many editors save in multiple bursts. */
    private static final long DEBOUNCE_MS = 500L;

    @Inject
    FileBO fileBO;

    @Inject
    ConnectorBO connectorBO;

    @Inject
    ConnectorRunBO connectorRunBO;

    private final Map<String, Thread> watchers = new ConcurrentHashMap<>();

    public void watch(String name, String etlDataPath, Long connectorId, String keycloakId) {
        if (etlDataPath == null || etlDataPath.isBlank()) {
            return;
        }
        if (watchers.containsKey(name)) {
            Log.warnf("EtlDataFileWatcher '%s': already registered, skipping", name);
            return;
        }
        Path file = Paths.get(etlDataPath).toAbsolutePath();
        Path dir = file.getParent();
        if (dir == null || !dir.toFile().isDirectory()) {
            Log.warnf("EtlDataFileWatcher '%s': parent directory of %s does not exist", name, etlDataPath);
            return;
        }

        Thread thread = new Thread(
                () -> runWatchLoop(name, dir, file, connectorId, keycloakId),
                "etl-data-watcher-" + name);
        thread.setDaemon(true);
        watchers.put(name, thread);
        thread.start();
        Log.infof("EtlDataFileWatcher '%s': watching %s for connector %d", name, file, connectorId);
    }

    private void runWatchLoop(String name, Path dir, Path file, Long connectorId, String keycloakId) {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            dir.register(ws,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
            long lastHandled = 0L;
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = ws.take();
                boolean relevant = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    Object ctx = event.context();
                    if (ctx instanceof Path changed && changed.getFileName().equals(file.getFileName())) {
                        relevant = true;
                    }
                }
                if (!key.reset()) {
                    Log.warnf("EtlDataFileWatcher '%s': watch key invalidated, stopping", name);
                    break;
                }
                long now = System.currentTimeMillis();
                if (relevant && (now - lastHandled) > DEBOUNCE_MS) {
                    lastHandled = now;
                    handleChange(name, file.toFile(), connectorId, keycloakId);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.infof("EtlDataFileWatcher '%s': interrupted, stopping", name);
        } catch (Exception e) {
            Log.errorf("EtlDataFileWatcher '%s': failed: %s", name, e.getMessage(), e);
        } finally {
            watchers.remove(name);
        }
    }

    private void handleChange(String name, File file, Long connectorId, String keycloakId) {
        if (!file.exists()) {
            Log.warnf("EtlDataFileWatcher '%s': file %s no longer exists, skipping", name, file);
            return;
        }
        try {
            Log.infof("EtlDataFileWatcher '%s': change detected for %s, re-uploading", name, file);
            FileEntity uploaded = fileBO.createEntityForSystem(file);

            ConnectorDTO connector = connectorBO.getById(connectorId);
            if (connector == null) {
                Log.warnf("EtlDataFileWatcher '%s': connector %d no longer exists", name, connectorId);
                return;
            }
            if (!(connector.getInputConfig() instanceof FileUploadSettingsDTO fileSettings)) {
                Log.warnf("EtlDataFileWatcher '%s': connector %d input is not a file upload, cannot re-attach",
                        name, connectorId);
                return;
            }
            fileSettings.setFileId(uploaded.getId());
            connectorBO.update(connectorId, connector, keycloakId);
            Log.infof("EtlDataFileWatcher '%s': re-attached file %d, starting run for connector %d",
                    name, uploaded.getId(), connectorId);
            connectorRunBO.startRun(connectorId, true, false, keycloakId);
        } catch (Exception e) {
            Log.errorf("EtlDataFileWatcher '%s': failed to re-process change: %s", name, e.getMessage(), e);
        }
    }

    @PreDestroy
    void shutdown() {
        watchers.values().forEach(Thread::interrupt);
        watchers.clear();
    }
}
