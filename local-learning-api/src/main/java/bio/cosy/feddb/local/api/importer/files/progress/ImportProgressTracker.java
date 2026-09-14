package bio.cosy.feddb.local.api.importer.files.progress;

import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDetailDTO;
import bio.cosy.feddb.local.api.importer.files.upload.ConnectorFileImportResultDTO;
import io.smallrye.reactive.messaging.annotations.Broadcast;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ImportProgressTracker {

    public static final String IMPORT_PROGRESS_CHANNEL = "import-progress";

    private static final Duration RETENTION = Duration.ofMinutes(30);

    private final Map<String, ImportProgressDTO> imports = new ConcurrentHashMap<>();
    private final Map<String, ImportEventDTO> terminalEvents = new ConcurrentHashMap<>();

    @Inject
    @Channel(IMPORT_PROGRESS_CHANNEL)
    @Broadcast
    @OnOverflow(OnOverflow.Strategy.DROP)
    Emitter<ImportEventDTO> progressEmitter;

    public void start(String importId, Long cohortId, Long connectorId, String fileName, Long fileSize) {
        if (importId == null || importId.isBlank()) {
            return;
        }
        forget();
        terminalEvents.remove(importId);
        imports.put(importId, new ImportProgressDTO(importId, cohortId, connectorId, fileName, fileSize));
    }

    public void phase(String importId, ImportPhase phase) {
        publish(importId, progress -> {
            progress.setPhase(phase);
            return ImportEventDTO.of(importId, phase);
        });
    }

    public void tablesFound(String importId, List<String> names) {
        publish(importId, progress -> {
            List<ImportTableDTO> tables = new ArrayList<>(names.size());
            for (int index = 0; index < names.size(); index++) {
                tables.add(ImportTableDTO.pending(names.get(index), index + 1, names.size()));
            }
            progress.setTables(tables);
            ImportEventDTO event = ImportEventDTO.of(importId, progress.getPhase());
            event.setTables(List.copyOf(tables));
            return event;
        });
    }

    public void table(String importId, ImportTableDTO table) {
        publish(importId, progress -> {
            replace(progress, table);
            ImportEventDTO event = ImportEventDTO.of(importId, progress.getPhase());
            event.setTable(table);
            return event;
        });
    }

    public void finished(String importId, ConnectorFileImportResultDTO result) {
        boolean refused = Boolean.FALSE.equals(result.getAccepted());
        ConnectorFilesDetailDTO stored = result.getFiles().isEmpty() ? null : result.getFiles().getFirst();
        publish(importId, progress -> {
            progress.setPhase(refused ? ImportPhase.REFUSED : ImportPhase.SUCCEEDED);
            progress.setAccepted(result.getAccepted());
            progress.setRefusal(result.getError());
            progress.setFileId(stored == null ? null : stored.getId());
            progress.setErrorMessage(refused && result.getError() != null
                    ? result.getError().getMessage() : null);
            progress.setFinishedAt(Instant.now());
            ImportEventDTO event = ImportEventDTO.of(importId, progress.getPhase());
            event.setResult(result);
            event.setErrorMessage(progress.getErrorMessage());
            event.setLast(true);
            return event;
        });
    }

    public void failed(String importId, String message) {
        publish(importId, progress -> {
            progress.setPhase(ImportPhase.FAILED);
            progress.setErrorMessage(message);
            progress.setFinishedAt(Instant.now());
            ImportEventDTO event = ImportEventDTO.of(importId, ImportPhase.FAILED);
            event.setErrorMessage(message);
            event.setLast(true);
            return event;
        });
    }

    public ImportProgressDTO get(String importId) {
        return importId == null ? null : imports.get(importId);
    }

    public ImportEventDTO terminalEvent(String importId) {
        return importId == null ? null : terminalEvents.get(importId);
    }


    public List<ImportProgressDTO> forCohort(Long cohortId, Long connectorId) {
        forget();
        return imports.values().stream()
                .filter(progress -> cohortId == null || cohortId.equals(progress.getCohortId()))
                .filter(progress -> connectorId == null || connectorId.equals(progress.getConnectorId()))
                .sorted(Comparator.comparing(ImportProgressDTO::getStartedAt).reversed())
                .toList();
    }

    private void publish(String importId, EventBuilder builder) {
        ImportProgressDTO progress = importId == null ? null : imports.get(importId);
        if (progress == null) {
            return;
        }
        ImportEventDTO event;
        synchronized (progress) {
            event = builder.build(progress);
            progress.setUpdatedAt(event.getAt());
            if (event.isLast()) {
                terminalEvents.put(importId, event);
            }
        }
        progressEmitter.send(event);
    }

    private void forget() {
        Instant cutoff = Instant.now().minus(RETENTION);
        imports.entrySet().removeIf(entry -> {
            Instant finishedAt = entry.getValue().getFinishedAt();
            if (finishedAt == null || !finishedAt.isBefore(cutoff)) {
                return false;
            }
            terminalEvents.remove(entry.getKey());
            return true;
        });
    }

    private static void replace(ImportProgressDTO progress, ImportTableDTO table) {
        List<ImportTableDTO> tables = new ArrayList<>(progress.getTables());
        int index = -1;
        for (int position = 0; position < tables.size(); position++) {
            if (tables.get(position).getName().equals(table.getName())) {
                index = position;
                break;
            }
        }
        if (index < 0) {
            tables.add(table);
        } else {
            tables.set(index, table);
        }
        tables.sort(Comparator.comparingInt(ImportTableDTO::getPosition));
        progress.setTables(tables);
    }

    @FunctionalInterface
    private interface EventBuilder {
        ImportEventDTO build(ImportProgressDTO progress);
    }
}
