package bio.cosy.feddb.local.api.importer.files;

import bio.cosy.feddb.core.api.file.FileResult;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.importer.files.progress.ImportEventDTO;
import bio.cosy.feddb.local.api.importer.files.progress.ImportProgressDTO;
import bio.cosy.feddb.local.api.importer.files.progress.ImportProgressTracker;
import bio.cosy.feddb.local.api.importer.files.upload.ConnectorFileImportRunner;
import bio.cosy.feddb.local.api.importer.files.upload.DetachedUpload;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static bio.cosy.feddb.local.api.importer.files.progress.ImportProgressTracker.IMPORT_PROGRESS_CHANNEL;


@ApplicationScoped
@Blocking
public class ConnectorFilesServiceImpl implements ConnectorFilesService {

    @Inject
    ConnectorFilesBO bo;

    @Inject
    ConnectorFileImportRunner runner;

    @Inject
    ImportProgressTracker progress;

    @Inject
    @Channel(IMPORT_PROGRESS_CHANNEL)
    Multi<ImportEventDTO> progressEvents;

    @Inject
    UserIdentity userIdentity;


    @Override
    public Multi<ImportEventDTO> importFilesStreaming(
            Long cohortId,
            ConnectorFileUploadRequestDTO request,
            Long connectorId,
            String importId
    ) {
        // An import nobody named is still worth watching: the stream is the answer, so it needs
        // something to carry.
        String id = importId == null || importId.isBlank() ? UUID.randomUUID().toString() : importId;
        request.setFiles(request.getFiles().stream().map(DetachedUpload::of).toList());
        FileUpload first = request.getFiles().isEmpty() ? null : request.getFiles().getFirst();
        progress.start(id, cohortId, connectorId,
                first == null ? null : first.fileName(),
                first == null ? null : first.size());
        String keycloakId = userIdentity.getKeycloakId();
        return eventsFor(id).onSubscription().invoke(() ->
                Infrastructure.getDefaultWorkerPool().execute(
                        () -> runner.runDetached(cohortId, request, connectorId, id, keycloakId)));
    }

    @Override
    public Multi<ImportEventDTO> followImport(String importId) {
        ImportProgressDTO current = progress.get(importId);
        if (current == null) {
            throw new NotFoundException("No import found with id: " + importId);
        }
        ImportEventDTO terminal = progress.terminalEvent(importId);
        return terminal == null ? eventsFor(importId) : Multi.createFrom().item(terminal);
    }

    /** The named import's live channel, closed by its terminal event. */
    private Multi<ImportEventDTO> eventsFor(String importId) {
        return Multi.createFrom().emitter(emitter -> {
            Cancellable subscription = progressEvents
                    .select().where(event -> importId.equals(event.getImportId()))
                    .subscribe().with(event -> {
                        emitter.emit(event);
                        if (event.isLast()) {
                            emitter.complete();
                        }
                    }, emitter::fail);
            emitter.onTermination(subscription::cancel);
        });
    }

    @Override
    public ImportProgressDTO getImport(String importId) {
        ImportProgressDTO found = progress.get(importId);
        if (found == null) {
            throw new NotFoundException("No import found with id: " + importId);
        }
        return found;
    }

    @Override
    public List<ImportProgressDTO> getImports(Long cohortId, Long connectorId) {
        return progress.forCohort(cohortId, connectorId);
    }

    @Override
    @Transactional
    public List<ConnectorFilesDTO> getFiles(Long cohortId) {
        return bo.getFiles(cohortId);
    }

    @Override
    @Transactional
    public Response deleteFile(Long cohortId, Long fileId) {
        if (!bo.deleteFile(cohortId, fileId)) {
            throw new NotFoundException("No file " + fileId + " in cohort " + cohortId);
        }
        return Response.ok().build();
    }

    @Override
    @Transactional
    public ConnectorFilesDetailDTO getFileInfo(Long cohortId, Long fileId) {
        return bo.getById(cohortId, fileId);
    }

    @Override
    @Transactional
    public ConnectorFilesDetailDTO getFileInfo(Long cohortId) {
        return bo.getFileInfo(cohortId);
    }

    @Override
    @Transactional
    public Response downloadConnectorFile(Long cohortId, Long fileId) {
        FileResult fileResult = bo.downloadConnectorFile(cohortId, fileId);
        File file = fileResult.file();
        if (!file.exists()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("File not found: " + fileId)
                    .build();
        }
        return Response.ok(file)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileResult.fileName() + "\"")
                .header("Access-Control-Expose-Headers",
                        "Content-Disposition, Content-Length, Content-Type")
                .build();
    }
}
