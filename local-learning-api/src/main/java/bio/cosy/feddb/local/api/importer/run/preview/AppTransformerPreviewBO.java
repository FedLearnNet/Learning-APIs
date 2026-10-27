package bio.cosy.feddb.local.api.importer.run.preview;

import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.failure.FailureCategory;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import bio.cosy.feddb.local.api.importer.files.table.ColumnNames;
import bio.cosy.feddb.local.api.importer.transformer.AppTransformerSessionBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;


@ApplicationScoped
public class AppTransformerPreviewBO {

    @Inject
    ConnectorPreviewBO previewBO;

    @Inject
    AppTransformerSessionBO sessionBO;

    @Inject
    ExecutionRunBO runBO;

    @Inject
    AppTransformerPreviewMapper mapper;

    @Inject
    Instance<AppTransformerPreviewBO> self;

    @ActivateRequestContext
    public Multi<AppTransformerPreviewStreamDTO> start(AppTransformerPreviewRequestDTO request, String keycloakId) {
        if (request == null || request.getConfig() == null) {
            throw new BadRequestException("A connector configuration is required to run a transformation step");
        }
        if (request.getStepIndex() == null) {
            throw new BadRequestException("A transformation step index is required");
        }
        int stepIndex = request.getStepIndex();
        ConnectorConfigDTO config = request.getConfig();
        ConnectorPreviewAppStageInput input = previewBO.prepareAppStage(config, stepIndex);

        return Multi.createFrom().emitter(emitter ->
                Infrastructure.getDefaultExecutor().execute(
                        () -> self.get().run(config, stepIndex, input, keycloakId, emitter)));
    }

    // The app's run is followed while the preview lasts, the session keeps it running until it is closed.
    @ActivateRequestContext
    public void run(ConnectorConfigDTO config,
                    int stepIndex,
                    ConnectorPreviewAppStageInput input,
                    String keycloakId,
                    MultiEmitter<? super AppTransformerPreviewStreamDTO> emitter) {
        AtomicReference<ExecutionRunDTO> run = new AtomicReference<>();
        AtomicReference<Cancellable> following = new AtomicReference<>();
        Long sessionId = null;
        try {
            sessionId = sessionBO.openPreview(input.getTransformer(), keycloakId, created -> {
                run.set(created);
                emitter.emit(previewMessageOf(created, stepIndex, false, null));
                following.set(runBO.subscribe(created.getId()).subscribe().with(
                        changed -> emitter.emit(previewMessageOf(changed, stepIndex, false, null)),
                        failure -> Log.debugf("Stopped following preview run %d: %s", created.getId(), failure.getMessage())));
            });
            List<Map<String, Object>> rows = sessionBO.transform(sessionId, input.getTransformer(), input.getRows());
            previewBO.storeAppStage(config.getConnectorId(), stepIndex, input.getTransformer(),
                    input.getFingerprint(), rows, ColumnNames.withRowColumns(input.getColumns(), rows));
            Optional.ofNullable(following.get()).ifPresent(Cancellable::cancel);
            AppTransformerPreviewStreamDTO done = previewMessageOf(run.get(), stepIndex, true, null);
            done.setStatus(RunStatusTypes.FINISHED);
            done.setRowCount(rows.size());
            emitter.emit(done);
            Log.infof("App transformer '%s' produced %d preview row(s) for step %d",
                    input.getTransformer().getAppImage(), rows.size(), stepIndex);
        } catch (Exception e) {
            Log.errorf("App transformer preview of step %d failed: %s", stepIndex, e.getMessage());
            Optional.ofNullable(following.get()).ifPresent(Cancellable::cancel);
            emitter.emit(previewMessageOf(run.get(), stepIndex, true, e.getMessage()));
        } finally {
            if (sessionId != null) {
                sessionBO.close(sessionId);
            }
            emitter.complete();
        }
    }

    private AppTransformerPreviewStreamDTO previewMessageOf(ExecutionRunDTO run, int stepIndex, boolean finished, String error) {
        AppTransformerPreviewStreamDTO message = run == null ? new AppTransformerPreviewStreamDTO() : mapper.runToStreamDTO(run);
        message.setStepIndex(stepIndex);
        message.setFinished(finished);
        if (error != null) {
            message.setStatus(RunStatusTypes.ERROR);
            message.setFailure(new ExecutionFailure(FailureCategory.TOOL, error));
        }
        return message;
    }
}
